package org.ff4j.store;

/*
 * #%L ff4j-store-jdbc %% Copyright (C) 2013 Ff4J %% Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License. #L%
 */

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.ff4j.core.Feature;
import org.ff4j.core.FeatureStore;
import org.ff4j.exception.FeatureAlreadyExistException;
import org.ff4j.exception.FeatureNotFoundException;
import org.ff4j.exception.GroupNotFoundException;
import org.ff4j.store.mongodb.FeatureDBObjectBuilder;
import org.ff4j.store.mongodb.FeatureDBObjectMapper;
import org.ff4j.store.mongodb.FeatureStoreMongoConstants;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

/**
 * Implementation of {@link FeatureStore} to work with MongoDB.
 * 
 * @author William Delanoue (@twillouer) </a>
 * @author <a href="mailto:cedrick.lunven@gmail.com">Cedrick LUNVEN</a>
 */
public class FeatureStoreMongoDB extends AbstractFeatureStore implements FeatureStoreMongoConstants {

    /** Map from DBObject to Feature. */
    private static final FeatureDBObjectMapper MAPPER = new FeatureDBObjectMapper();

    /** Build fields. */
    private static final FeatureDBObjectBuilder BUILDER = new FeatureDBObjectBuilder();

    /** MongoDB collection. */
    private final DBCollection collection;

    /**
     * Parameterized constructor with collection.
     * 
     * @param collection
     *            the collection to set
     */
    public FeatureStoreMongoDB(DBCollection collection) {
        this.collection = collection;
    }
    
    /**
     * Parameterized constructor with collection.
     * 
     * @param collection
     *            the collection to set
     */
    public FeatureStoreMongoDB(DBCollection collection, String xmlConfFile) {
        this.collection = collection;
        importFeaturesFromXmlFile(xmlConfFile);
    }

    /** {@inheritDoc} */
    @Override
    public void enable(String featId) {
        this.updateStatus(featId, true);
    }

    /** {@inheritDoc} */
    @Override
    public void disable(String featId) {
        this.updateStatus(featId, false);
    }

    /**
     * Update status of feature.
     * 
     * @param uid
     *            feature id
     * @param enable
     *            enabler
     */
    private void updateStatus(String uid, boolean enable) {
        if (uid == null || uid.isEmpty()) {
            throw new IllegalArgumentException("Feature identifier cannot be null nor empty");
        }
        if (!exist(uid)) {
            throw new FeatureNotFoundException(uid);
        }
        DBObject target = BUILDER.getFeatUid(uid);
        Object enabledd = BUILDER.getEnable(enable);
        collection.update(target, BasicDBObjectBuilder.start(MONGO_SET, enabledd).get());
    }

    /** {@inheritDoc} */
    @Override
    public boolean exist(String featId) {
        return 1 == collection.count(BUILDER.getFeatUid(featId));
    }

    /** {@inheritDoc} */
    @Override
    public Feature read(String uid) {
        if (uid == null || uid.isEmpty()) {
            throw new IllegalArgumentException("Feature identifier cannot be null nor empty");
        }
        DBObject object = collection.findOne(BUILDER.getFeatUid(uid));
        if (object==null) {
            throw new FeatureNotFoundException(uid);
        }
        return MAPPER.mapFeature(object);
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
        collection.save(MAPPER.toDBObject(fp));
    }

    /** {@inheritDoc} */
    @Override
    public void delete(String uid) {
        if (uid == null || uid.isEmpty()) {
            throw new IllegalArgumentException("Feature identifier cannot be null nor empty");
        }
        if (!exist(uid)) {
            throw new FeatureNotFoundException(uid);
        }
        collection.remove(BUILDER.getFeatUid(uid));
    }

    /** {@inheritDoc} */
    @Override
    public void grantRoleOnFeature(String uid, String roleName) {
        if (uid == null || uid.isEmpty()) {
            throw new IllegalArgumentException("Feature identifier cannot be null nor empty");
        }
        if (roleName == null || roleName.isEmpty()) {
            throw new IllegalArgumentException("roleName cannot be null nor empty");
        }
        if (!exist(uid)) {
            throw new FeatureNotFoundException(uid);
        }
        collection.update(BUILDER.getFeatUid(uid), new BasicDBObject("$addToSet", BUILDER.getRoles(roleName)));
    }

    /** {@inheritDoc} */
    @Override
    public void removeRoleFromFeature(String uid, String roleName) {
        if (uid == null || uid.isEmpty()) {
            throw new IllegalArgumentException("Feature identifier cannot be null nor empty");
        }
        if (roleName == null || roleName.isEmpty()) {
            throw new IllegalArgumentException("roleName cannot be null nor empty");
        }
        if (!exist(uid)) {
            throw new FeatureNotFoundException(uid);
        }
        collection.update(BUILDER.getFeatUid(uid), new BasicDBObject("$pull", BUILDER.getRoles(roleName)));
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Feature> readAll() {
        LinkedHashMap<String, Feature> mapFP = new LinkedHashMap<String, Feature>();
        for(DBObject dbObject : collection.find()) {
            Feature feature = MAPPER.mapFeature(dbObject);
            mapFP.put(feature.getUid(), feature);
        }
        return mapFP;
    }

    /** {@inheritDoc} */
    @Override
    public void update(Feature fp) {
        if (fp == null) {
            throw new IllegalArgumentException("Feature cannot be null nor empty");
        }
        Feature fpExist = read(fp.getUid());
        collection.save(MAPPER.toDBObject(fp));
        // enable/disable
        if (fp.isEnable() != fpExist.isEnable()) {
            if (fp.isEnable()) {
                enable(fp.getUid());
            } else {
                disable(fp.getUid());
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean existGroup(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            throw new IllegalArgumentException("Groupname cannot be null nor empty");
        }
        return collection.count(BUILDER.getGroupName(groupName)) > 0;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> readAllGroups() {
        Set<String> setOfGroups = new HashSet<String>();
        for (DBObject dbObject : collection.find()) {
            setOfGroups.add((String) dbObject.get(GROUPNAME));
        }
        setOfGroups.remove(null);
        setOfGroups.remove("");
        return setOfGroups;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Feature> readGroup(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            throw new IllegalArgumentException("Groupname cannot be null nor empty");
        }
        if (!existGroup(groupName)) {
            throw new GroupNotFoundException(groupName);
        }
        LinkedHashMap<String, Feature> mapFP = new LinkedHashMap<String, Feature>();
        for (DBObject dbObject : collection.find(BUILDER.getGroupName(groupName))) {
            Feature feature = MAPPER.mapFeature(dbObject);
            mapFP.put(feature.getUid(), feature);
        }
        return mapFP;
    }

    /** {@inheritDoc} */
    @Override
    public void enableGroup(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            throw new IllegalArgumentException("Groupname cannot be null nor empty");
        }
        if (!existGroup(groupName)) {
            throw new GroupNotFoundException(groupName);
        }
        for (DBObject dbObject : collection.find(BUILDER.getGroupName(groupName))) {
            Object enabledd = BUILDER.getEnable(true);
            collection.update(dbObject, BasicDBObjectBuilder.start(MONGO_SET, enabledd).get());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void disableGroup(String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            throw new IllegalArgumentException("Groupname cannot be null nor empty");
        }
        if (!existGroup(groupName)) {
            throw new GroupNotFoundException(groupName);
        }
        for (DBObject dbObject : collection.find(BUILDER.getGroupName(groupName))) {
            Object enabledd = BUILDER.getEnable(false);
            collection.update(dbObject, BasicDBObjectBuilder.start(MONGO_SET, enabledd).get());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void addToGroup(String uid, String groupName) {
        if (uid == null || uid.isEmpty()) {
            throw new IllegalArgumentException("Feature identifier cannot be null nor empty");
        }
        if (groupName == null || groupName.isEmpty()) {
            throw new IllegalArgumentException("Groupname cannot be null nor empty");
        }
        if (!exist(uid)) {
            throw new FeatureNotFoundException(uid);
        }
        DBObject target = BUILDER.getFeatUid(uid);
        DBObject nGroupName = BUILDER.getGroupName(groupName);
        collection.update(target, BasicDBObjectBuilder.start(MONGO_SET, nGroupName).get());
    }

    /** {@inheritDoc} */
    @Override
    public void removeFromGroup(String uid, String groupName) {
        if (uid == null || uid.isEmpty()) {
            throw new IllegalArgumentException("Feature identifier cannot be null nor empty");
        }
        if (groupName == null || groupName.isEmpty()) {
            throw new IllegalArgumentException("Groupname cannot be null nor empty");
        }
        if (!exist(uid)) {
            throw new FeatureNotFoundException(uid);
        }
        if (!existGroup(groupName)) {
            throw new GroupNotFoundException(groupName);
        }
        DBObject target = BUILDER.getFeatUid(uid);
        DBObject nGroupName = BUILDER.getGroupName("");
        collection.update(target, BasicDBObjectBuilder.start(MONGO_SET, nGroupName).get());
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"type\":\"" + this.getClass().getCanonicalName() + "\"");
        sb.append("\"mongodb\":\"" + this.collection.getName() + "\"");
        sb.append(",\"cached\":" + this.isCached());
        if (this.isCached()) {
            sb.append(",\"cacheProvider\":\"" + this.getCacheProvider() + "\"");
            sb.append(",\"cacheStore\":\"" + this.getCachedTargetStore() + "\"");
        }
        Set<String> myFeatures = readAll().keySet();
        sb.append(",\"numberOfFeatures\":" + myFeatures.size());
        sb.append(",\"features\":[");
        boolean first = true;
        for (String myFeature : myFeatures) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"" + myFeature + "\"");
        }
        Set<String> myGroups = readAllGroups();
        sb.append("],\"numberOfGroups\":" + myGroups.size());
        sb.append(",\"groups\":[");
        first = true;
        for (String myGroup : myGroups) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("\"" + myGroup + "\"");
        }
        sb.append("]");
        sb.append("}");
        return sb.toString();
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
