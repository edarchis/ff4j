package org.ff4j.cache;

/*
 * #%L ff4j-cache-ehcache %% Copyright (C) 2013 Ff4J %% Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License. #L%
 */

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.ff4j.FF4j;
import org.ff4j.core.Feature;
import org.ff4j.exception.FeatureAlreadyExistException;
import org.ff4j.exception.FeatureNotFoundException;
import org.ff4j.store.FeatureStore;
import org.junit.Test;

public abstract class AbstractStoreTest extends TestCase {

    private FF4j ff4j;

    // Tested Store
    protected FeatureStore testedStore;

    /** {@inheritDoc} */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        testedStore = initStore();
        ff4j = new FF4j("ff4j.xml").autoCreate(false);
        ff4j.setStore(testedStore);
    }

    protected abstract FeatureStore initStore() throws Exception;

    @Test
    public void testFlipWithInvalidName_NotFoundException() {
        try {
            ff4j.isFlipped("dummyDum");
            fail("Dummy should not exist");
        } catch (FeatureNotFoundException fue) {
            Assert.assertTrue(fue.getMessage().contains("dummy"));
        }
    }

    @Test
    public void testEnableorDisable_NotFoundException() {
        try {
            ff4j.enable("dummy");
            fail();
        } catch (FeatureNotFoundException fue) {
            Assert.assertTrue(fue.getMessage().contains("dummy"));
        }
    }

    @Test
    public void testEnableFeature() {
        ff4j.enable("first");
        Assert.assertTrue(ff4j.isFlipped("first"));
    }

    @Test
    public void testDisableFeature() {
        ff4j.disable("first");
        Assert.assertFalse(ff4j.isFlipped("first"));
    }

    @Test
    public void testAddFlipPoint() throws Exception {
        Set<String> rights = new HashSet<String>(Arrays.asList(new String[] {"ROLE_USER"}));
        Feature fp = new Feature("new", true, "description", rights);
        testedStore.create(fp);
        Assert.assertEquals(6, testedStore.readAll().size());
        Assert.assertNotNull(testedStore.read("new"));
    }

    @Test
    public void testAddFlipPoint_AlreadyExist_AlreadyExisException() throws Exception {
        Feature fp = new Feature("new", true, "description");
        testedStore.create(fp);

        Set<String> rights = new HashSet<String>(Arrays.asList(new String[] {"ROLE_USER"}));
        Feature fp2 = new Feature("new", true, "description", rights);
        try {
            testedStore.create(fp2);
            fail();
        } catch (FeatureAlreadyExistException fpaee) {
            // OK
        }

        // Overriding, no error, no new creation
        Assert.assertEquals(6, testedStore.readAll().size());
        Assert.assertNotNull(testedStore.read("new"));
    }

    @Test
    public void testDeleteFlipPoint() throws Exception {
        Set<String> rights = new HashSet<String>(Arrays.asList(new String[] {"ROLE_USER"}));
        Feature fp2 = new Feature("new", true, "description", rights);
        testedStore.create(fp2);
        Assert.assertEquals(6, testedStore.readAll().size());
        testedStore.delete(fp2.getUid());
        Assert.assertEquals(5, testedStore.readAll().size());
    }

    @Test
    public void testDeteleFlipPoint_DoesnotExistReturnError() throws Exception {
        try {
            testedStore.delete("does-not-exist");
            fail();
        } catch (FeatureNotFoundException fnfe) {
            // ok : @Test(expected = FlipPointNotFoundException.class) doesn't work ???
        }
    }

    @Test
    public void testAddRoleToFlipPointThatDoesNotExistReturnException() throws Exception {
        testedStore.grantRoleOnFeature("first", "role-does-not-exit");
    }

    @Test
    public void testDeleteRole_ToFlipPoint() throws Exception {
        testedStore.removeRoleFromFeature("first", "ROLE_USER");
        Assert.assertEquals(0, testedStore.read("first").getAuthorizations().size());
    }

    @Test
    public void testUpdateFlip_CoreData() throws Exception {
        Feature fpBis = new Feature("first", false, "desc2");
        testedStore.update(fpBis);
        Assert.assertFalse(testedStore.read("first").isEnable());
        Assert.assertEquals("desc2", testedStore.read("first").getDescription());
    }

    @Test
    public void testUpdateFlip_MoreAutorisation() throws Exception {
        Set<String> rights2 = new HashSet<String>(Arrays.asList(new String[] {"ROLE_USER","X"}));
        Feature fpBis = new Feature("first", false, "desc2", rights2);
        testedStore.update(fpBis);
        Assert.assertEquals(2, testedStore.read("first").getAuthorizations().size());
    }

    @Test
    public void testUpdateFlip_LessAutorisation() {
        Feature fpBis = new Feature("first", false, null);
        testedStore.update(fpBis);
        Assert.assertEquals(0, testedStore.read("first").getAuthorizations().size());
    }

    @Test
    public void testUpdateFlip_MoreAutorisationNotExist_ReturnError() {
        Set<String> rights2 = new HashSet<String>(Arrays.asList(new String[] {"ROLE_USER","ROLE_ADMIN"}));
        Feature fpBis = new Feature("first", false, "desc2", rights2);
        testedStore.update(fpBis);
        Assert.assertEquals(2, testedStore.read("first").getAuthorizations().size());
    }

}
