package com.laurosantos.clippr.persistence;

import com.laurosantos.clippr.domain.AppSettings;
import com.laurosantos.clippr.domain.ClipCandidate;
import com.laurosantos.clippr.domain.ProcessingStatus;
import com.laurosantos.clippr.domain.VideoSource;
import com.laurosantos.clippr.persistence.entities.ClipEntity;
import com.laurosantos.clippr.persistence.entities.SettingsEntity;
import com.laurosantos.clippr.persistence.entities.VideoEntity;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final DatabaseManager INSTANCE = new DatabaseManager();

    private HikariDataSource dataSource;
    private EntityManagerFactory entityManagerFactory;
    private volatile boolean initialized = false;

    private DatabaseManager() {}

    public static DatabaseManager getInstance() {
        return INSTANCE;
    }

    public synchronized boolean init() {
        if (initialized) return true;

        try {
            logger.info("Inicializando conexão com o PostgreSQL...");
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(System.getProperty("clippr.db.url", "jdbc:postgresql://localhost:5432/clippr_db"));
            config.setUsername(System.getProperty("clippr.db.user", "clippr"));
            config.setPassword(System.getProperty("clippr.db.password", "clippr_secret_password"));
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(3000); // 3s timeout
            config.setInitializationFailTimeout(2000);

            this.dataSource = new HikariDataSource(config);

            Properties props = new Properties();
            props.put(AvailableSettings.JAKARTA_NON_JTA_DATASOURCE, dataSource);
            props.put(AvailableSettings.HBM2DDL_AUTO, "update");
            props.put(AvailableSettings.SHOW_SQL, "false");
            props.put(AvailableSettings.HIGHLIGHT_SQL, "false");

            Configuration configuration = new Configuration();
            configuration.setProperties(props);
            configuration.addAnnotatedClass(VideoEntity.class);
            configuration.addAnnotatedClass(ClipEntity.class);
            configuration.addAnnotatedClass(SettingsEntity.class);

            this.entityManagerFactory = configuration.buildSessionFactory();
            this.initialized = true;
            logger.info("Banco de dados PostgreSQL e Hibernate inicializados com sucesso.");
            return true;
        } catch (Exception ex) {
            logger.warn("Não foi possível conectar ao PostgreSQL: {}. (O container Docker pode estar desligado)", ex.getMessage());
            this.initialized = false;
            return false;
        }
    }

    public boolean isConnected() {
        if (!initialized || dataSource == null || dataSource.isClosed()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    public VideoSource saveVideo(VideoSource source) {
        if (!init()) return source;
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            VideoEntity entity = VideoEntity.fromDomain(source);
            if (entity.getId() == null) {
                em.persist(entity);
            } else {
                entity = em.merge(entity);
            }
            em.getTransaction().commit();
            return entity.toDomain();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            logger.error("Erro ao salvar vídeo no banco: {}", e.getMessage(), e);
            return source;
        } finally {
            em.close();
        }
    }

    public void updateVideoStatus(Long videoId, ProcessingStatus status, String errorMessage) {
        if (!init() || videoId == null) return;
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            VideoEntity entity = em.find(VideoEntity.class, videoId);
            if (entity != null) {
                entity.setStatus(status);
                entity.setErrorMessage(errorMessage);
                em.merge(entity);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            logger.error("Erro ao atualizar status do vídeo: {}", e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    public List<ClipCandidate> saveClips(Long videoSourceId, List<ClipCandidate> candidates) {
        if (!init() || videoSourceId == null || candidates.isEmpty()) return candidates;
        EntityManager em = entityManagerFactory.createEntityManager();
        List<ClipCandidate> savedList = new ArrayList<>();
        try {
            em.getTransaction().begin();
            VideoEntity video = em.find(VideoEntity.class, videoSourceId);
            if (video != null) {
                for (ClipCandidate candidate : candidates) {
                    ClipEntity entity = ClipEntity.fromDomain(candidate, video);
                    if (entity.getId() == null) {
                        em.persist(entity);
                    } else {
                        entity = em.merge(entity);
                    }
                    savedList.add(entity.toDomain());
                }
            }
            em.getTransaction().commit();
            return savedList;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            logger.error("Erro ao salvar clipes: {}", e.getMessage(), e);
            return candidates;
        } finally {
            em.close();
        }
    }

    public void updateClipTimeBounds(Long clipId, double startSeconds, double endSeconds) {
        if (!init() || clipId == null) return;
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            ClipEntity entity = em.find(ClipEntity.class, clipId);
            if (entity != null) {
                entity.setStartSeconds(startSeconds);
                entity.setEndSeconds(endSeconds);
                em.merge(entity);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            logger.error("Erro ao atualizar timestamps do clipe {}: {}", clipId, e.getMessage(), e);
        } finally {
            em.close();
        }
    }

    public AppSettings getSettings() {
        if (!init()) return AppSettings.defaultSettings();
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            SettingsEntity entity = em.find(SettingsEntity.class, 1L);
            if (entity != null) {
                return entity.toDomain();
            }
            AppSettings defaults = AppSettings.defaultSettings();
            saveSettings(defaults);
            return defaults;
        } catch (Exception e) {
            logger.error("Erro ao carregar configurações: {}", e.getMessage());
            return AppSettings.defaultSettings();
        } finally {
            em.close();
        }
    }

    public void saveSettings(AppSettings settings) {
        if (!init()) return;
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            em.getTransaction().begin();
            SettingsEntity entity = SettingsEntity.fromDomain(settings);
            em.merge(entity);
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            logger.error("Erro ao salvar configurações: {}", e.getMessage());
        } finally {
            em.close();
        }
    }

    public synchronized void close() {
        if (entityManagerFactory != null && entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
        initialized = false;
    }
}
