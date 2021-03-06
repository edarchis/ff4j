package org.ff4j.store;

/*
 * #%L
 * ff4j-store-redis
 * %%
 * Copyright (C) 2013 - 2014 Ff4J
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.ff4j.core.Feature;
import org.ff4j.core.FeatureStore;
import org.ff4j.exception.FeatureAlreadyExistException;
import org.ff4j.exception.FeatureNotFoundException;
import org.ff4j.exception.GroupNotFoundException;
import org.ff4j.redis.FF4JRedisConstants;
import org.ff4j.utils.Util;
import org.ff4j.utils.json.FeatureJsonParser;

import redis.clients.jedis.Jedis;

/**
 * {@link FeatureStore} to persist data into
 *
 * @author <a href="mailto:cedrick.lunven@gmail.com">Cedrick LUNVEN</a>
 */
public class FeatureStoreRedis extends AbstractFeatureStore implements FF4JRedisConstants {
    
    /** redis host. */
    protected String redisHost = DEFAULT_REDIS_HOST;

    /** redis port. */
    protected int redisport = DEFAULT_REDIS_PORT;

    /** time to live. */
    protected int timeToLive = DEFAULT_TTL;
    
    /** Java Redis CLIENT. */
    protected Jedis jedis;
    
    /**
     * Default Constructor.
     */
    public FeatureStoreRedis() {
        jedis = new Jedis(redisHost, redisport);
    }
    
    /**
     * Default Constructor.
     */
    public FeatureStoreRedis(String xmlFeaturesfFile) {
       this();
       importFeaturesFromXmlFile(xmlFeaturesfFile);
    }

    /**
     * Contact remote redis server.
     * 
     * @param host
     *            target redis host
     * @param port
     *            target redis port
     */
    public FeatureStoreRedis(String host, int port) {
        this.redisHost = host;
        this.redisport = port;
        jedis = new Jedis(host, port);
    }

    /**
     * Contact remote redis server.
     * 
     * @param host
     *            target redis host
     * @param port
     *            target redis port
     */
    public FeatureStoreRedis(String host, int port, String xmlFeaturesfFile) {
        this(host, port);
        importFeaturesFromXmlFile(xmlFeaturesfFile);
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean exist(String uid) {
        Util.assertParamNotNull(uid, "Feature identifier");
        return jedis.exists(PREFIX_KEY + uid);
    }
    
    /** {@inheritDoc} */
    @Override
    public Feature read(String uid) {
        if (!exist(uid)) {
            throw new FeatureNotFoundException(uid);
        }
        return FeatureJsonParser.parseFeature(jedis.get(PREFIX_KEY + uid));
    }
    
    /** {@inheritDoc} */
    @Override
    public void update(Feature fp) {
        if (fp == null) {
            throw new IllegalArgumentException("Feature cannot be null");
        }
        if (!exist(fp.getUid())) {
            throw new FeatureNotFoundException(fp.getUid());
        }
        jedis.set(PREFIX_KEY + fp.getUid(), fp.toJson());
        jedis.persist(PREFIX_KEY + fp.getUid());
    }
    
    /** {@inheritDoc} */
    @Override
    public void enable(String uid) {
        // Read from redis, feature not found if no present
        Feature f = read(uid);
        // Update within Object
        f.enable();
        // Serialization and update key, update TTL
        update(f);
    }

    /** {@inheritDoc} */
    @Override
    public void disable(String uid) {
        // Read from redis, feature not found if no present
        Feature f = read(uid);
        // Update within Object
        f.disable();
        // Serialization and update key, update TTL
        update(f);
    }

    /** {@inheritDoc} */
    @Override
    public void create(Feature fp) {
        if (fp == null) {
            throw new IllegalArgumentException("Feature cannot be null nor empty");
        }
        if (exist(fp.getUid())) {
            throw new FeatureAlreadyExistException(fp.getUid());
        }
        jedis.set(PREFIX_KEY + fp.getUid(), fp.toJson());
        jedis.persist(PREFIX_KEY + fp.getUid());
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Feature> readAll() {
        Set < String > myKeys = jedis.keys(PREFIX_KEY + "*");
        Map<String, Feature> myMap = new HashMap<String, Feature>();
        if (myKeys != null) {
            for (String key : myKeys) {
                key = key.replaceAll(PREFIX_KEY, "");
                myMap.put(key, read(key));
            }
        }
        return myMap;
    }

    /** {@inheritDoc} */
    @Override
    public void delete(String fpId) {
        if (!exist(fpId)) {
            throw new FeatureNotFoundException(fpId);
        }
        jedis.del(PREFIX_KEY + fpId);
    }    

    /** {@inheritDoc} */
    @Override
    public void grantRoleOnFeature(String flipId, String roleName) {
        Util.assertParamNotNull(roleName, "roleName (#2)");
        // retrieve
        Feature f = read(flipId);
        // modify
        f.getPermissions().add(roleName);
        // persist modification
        update(f);
    }

    /** {@inheritDoc} */
    @Override
    public void removeRoleFromFeature(String flipId, String roleName) {
        Util.assertParamNotNull(roleName, "roleName (#2)");
        // retrieve
        Feature f = read(flipId);
        f.getPermissions().remove(roleName);
        // persist modification
        update(f);
    }
    
    /** {@inheritDoc} */
    @Override
    public Map<String, Feature> readGroup(String groupName) {
        Util.assertParamNotNull(groupName, "groupName");
        Map < String, Feature > features = readAll();
        Map < String, Feature > group = new HashMap<String, Feature>();
        for (String uid : features.keySet()) {
            if (groupName.equals(features.get(uid).getGroup())) {
                group.put(uid, features.get(uid));
            }
        }
        if (group.isEmpty()) {
            throw new GroupNotFoundException(groupName);
        }
        return group;
    }
    
    /** {@inheritDoc} */
    @Override
    public boolean existGroup(String groupName) {
        Util.assertParamNotNull(groupName, "groupName");
        Map < String, Feature > features = readAll();
        Map < String, Feature > group = new HashMap<String, Feature>();
        for (String uid : features.keySet()) {
            if (groupName.equals(features.get(uid).getGroup())) {
                group.put(uid, features.get(uid));
            }
        }
        return !group.isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public void enableGroup(String groupName) {
        Map < String, Feature > features = readGroup(groupName);
        for (String uid : features.keySet()) {
            features.get(uid).enable();
            update(features.get(uid));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void disableGroup(String groupName) {
        Map < String, Feature > features = readGroup(groupName);
        for (String uid : features.keySet()) {
            features.get(uid).disable();
            update(features.get(uid));
        }
    }

    /** {@inheritDoc} */
    @Override
    public void addToGroup(String featureId, String groupName) {
        Util.assertParamNotNull(groupName, "groupName (#2)");
        // retrieve
        Feature f = read(featureId);
        f.setGroup(groupName);
        // persist modification
        update(f);
    }

    /** {@inheritDoc} */
    @Override
    public void removeFromGroup(String featureId, String groupName) {
        Util.assertParamNotNull(groupName, "groupName (#2)");
        if (!existGroup(groupName)) {
            throw new GroupNotFoundException(groupName);
        }
        // retrieve
        Feature f = read(featureId);
        f.setGroup(null);
        // persist modification
        update(f);
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> readAllGroups() {
        Map < String, Feature > features = readAll();
        Set < String > groups = new HashSet<String>();
        for (String uid : features.keySet()) {
            groups.add(features.get(uid).getGroup());
        }
        groups.remove(null);
        return groups;
    }

    // -------- Overrided in cache proxy --------------

    /** {@inheritDoc} */
    @Override
    public boolean isCached() {
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public String getCacheProvider() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public String getCachedTargetStore() {
        return null;
    }
    
}
