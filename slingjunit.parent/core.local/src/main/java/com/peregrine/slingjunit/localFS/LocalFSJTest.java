package com.peregrine.slingjunit.localFS;

import com.peregrine.commons.util.PerUtil;
import com.peregrine.replication.PerReplicable;
import com.peregrine.replication.Replication;
import com.peregrine.replication.ReplicationsContainerWithDefault;
import com.peregrine.slingjunit.ReplicationTestBase;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.junit.annotations.SlingAnnotationsTestRunner;
import org.apache.sling.junit.annotations.TestReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.Calendar;
import static org.apache.jackrabbit.JcrConstants.JCR_LASTMODIFIED;
import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

@RunWith(SlingAnnotationsTestRunner.class)
public class LocalFSJTest extends ReplicationTestBase {

    public static final String LOCAL_FS = "localFS";
    private Calendar beforeTime;
    @TestReference
    private ResourceResolverFactory resolverFactory;
    private ResourceResolver adminResourceResolver = null;

//    NOTE: There may be up to 3 types of replication services registered: localFS, remote, local
//    If possible, remove OSGI configs for unused replication service implementations; remote and local service, before running this test.
//    Otherwise, since the following line is non-deterministic, it could inject any of the replication services configured.
    @TestReference
    private ReplicationsContainerWithDefault replications;
    private Replication replication;
    public static String INDEX = "/content/example/pages/index";
    public static String CONTACT = "/content/example/pages/contact";
    public static String JSON_DATA = ".data.json";
    public static String HTML = ".html";

    private Resource stellaImgRes;
    private Resource indexPageRes;
    private Resource contactPageRes;
    private static String STATIC_HOME = "./sling/staticreplication";

    @Before
    public void setup(){
        try {
            replication = replications.get(LOCAL_FS);
            beforeTime = Calendar.getInstance();
            adminResourceResolver = resolverFactory.getAdministrativeResourceResolver(null);
            stellaImgRes = adminResourceResolver.getResource(STELLA_PNG);
            indexPageRes = this.adminResourceResolver.getResource(INDEX);
            contactPageRes = this.adminResourceResolver.getResource(CONTACT);
        } catch (LoginException e) {
            fail(e.getMessage());
        }
    }

    @After
    public void cleanup(){
        deactivateResource(STELLA_PNG, stellaImgRes, replication);
        deactivateResource(INDEX, indexPageRes, replication);
        deactivateResource(INDEX, contactPageRes, replication);
        adminResourceResolver.close();
        adminResourceResolver = null;
    }

    @Test
    public void setupIsCorrect(){
        assertNotNull(replication);
        assertEquals(LOCAL_FS, replication.getName());
        assertNotNull(stellaImgRes);
    }

    @Test
    public void staticreplicationExists() {
        assertFileExists(STATIC_HOME);
    }


    @Test
    public void replicateOneAsset() {
        // assert test starts with the file unpublished
        assertFileDoesNotExist(STATIC_HOME+STELLA_PNG);
        PerReplicable stellaImgRepl = stellaImgRes.adaptTo(PerReplicable.class);
        assertFalse(stellaImgRepl.isReplicated());
        try {
            // publish the file
            replication.replicate(stellaImgRes, true, PerUtil.ADD_ALL_RESOURCE_CHECKER);
            assertFileExists(STATIC_HOME+STELLA_PNG);
            assertTrue(stellaImgRepl.isReplicated());
            // touch the asset modified date after publishing
            stellaImgRepl.getModifiableProperties().put(JCR_LASTMODIFIED, Calendar.getInstance());
            assertNotNull(stellaImgRepl.getReplicated());
            // verify the resource reports as stale
            assertTrue(stellaImgRepl.isStale());

        } catch (Replication.ReplicationException e) {
            fail(e.getMessage());
        }
        // unpublish the file
        deactivateResource(STELLA_PNG, stellaImgRes, replication);
        // assert the file is unpublished
        assertFileDoesNotExist(STATIC_HOME+STELLA_PNG);
        // verify the resource accurately reports publication status
        stellaImgRepl = adminResourceResolver.getResource(STELLA_PNG).adaptTo(PerReplicable.class);
        assertFalse(stellaImgRepl.isReplicated());
        assertFalse(stellaImgRepl.isStale());
    }

    @Test
    public void replicatePage(){
        assertFileDoesNotExist(STATIC_HOME+ INDEX + HTML);
        assertFileDoesNotExist(STATIC_HOME+ CONTACT + HTML);
        assertFileDoesNotExist(STATIC_HOME+ INDEX + JSON_DATA);
        assertFileDoesNotExist(STATIC_HOME+ CONTACT + JSON_DATA);

        assertNotNull(indexPageRes);
        assertNotNull(contactPageRes);

        PerReplicable indexRepl = indexPageRes.adaptTo(PerReplicable.class);
        PerReplicable contactRepl = contactPageRes.adaptTo(PerReplicable.class);
        assertNotNull(indexRepl);
        assertNotNull(contactRepl);

        try {
            replication.replicate(indexPageRes, true, PerUtil.ADD_ALL_RESOURCE_CHECKER);
            replication.replicate(contactPageRes, true, PerUtil.ADD_ALL_RESOURCE_CHECKER);
        } catch (Replication.ReplicationException e) {
            fail(e.getMessage());
        }
        // make published pages exist
        assertFileExists(STATIC_HOME+ INDEX + HTML);
        assertFileExists(STATIC_HOME+ INDEX + JSON_DATA);
        assertFileExists(STATIC_HOME+ CONTACT + HTML);
        assertFileExists(STATIC_HOME+ CONTACT + JSON_DATA);

        // make sure deactivation does not remove published sibling
        deactivateResource(CONTACT, contactPageRes, replication);
        assertFileDoesNotExist(STATIC_HOME+ CONTACT + JSON_DATA);
        assertFileDoesNotExist(STATIC_HOME+ CONTACT + HTML);
        assertFileExists(STATIC_HOME+ INDEX + HTML);
        assertFileExists(STATIC_HOME+ INDEX + JSON_DATA);

    }

    private void assertFileExists(String filePath){
        File file = new File(filePath);
        assertTrue(file.exists());
    }

    private void assertFileDoesNotExist(String filePath){
        File file = new File(filePath);
        assertFalse(file.exists());
    }
}
