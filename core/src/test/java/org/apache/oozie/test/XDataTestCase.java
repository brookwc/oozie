/**
 * Copyright (c) 2010 Yahoo! Inc. All rights reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. See accompanying LICENSE file.
 */
package org.apache.oozie.test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Date;
import java.util.regex.Matcher;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.oozie.BundleJobBean;
import org.apache.oozie.CoordinatorActionBean;
import org.apache.oozie.CoordinatorJobBean;
import org.apache.oozie.SLAEventBean;
import org.apache.oozie.WorkflowActionBean;
import org.apache.oozie.WorkflowJobBean;
import org.apache.oozie.action.hadoop.MapperReducerForTest;
import org.apache.oozie.client.CoordinatorAction;
import org.apache.oozie.client.CoordinatorJob;
import org.apache.oozie.client.Job;
import org.apache.oozie.client.OozieClient;
import org.apache.oozie.client.SLAEvent;
import org.apache.oozie.client.WorkflowAction;
import org.apache.oozie.client.WorkflowJob;
import org.apache.oozie.client.CoordinatorJob.Execution;
import org.apache.oozie.client.CoordinatorJob.Timeunit;
import org.apache.oozie.client.SLAEvent.Status;
import org.apache.oozie.command.CommandException;
import org.apache.oozie.command.jpa.BundleJobInsertCommand;
import org.apache.oozie.command.jpa.CoordActionInsertCommand;
import org.apache.oozie.command.jpa.CoordJobInsertCommand;
import org.apache.oozie.command.jpa.SLAEventInsertCommand;
import org.apache.oozie.command.jpa.WorkflowActionInsertCommand;
import org.apache.oozie.command.jpa.WorkflowJobInsertCommand;
import org.apache.oozie.service.JPAService;
import org.apache.oozie.service.Services;
import org.apache.oozie.service.UUIDService;
import org.apache.oozie.service.WorkflowAppService;
import org.apache.oozie.service.WorkflowStoreService;
import org.apache.oozie.service.UUIDService.ApplicationType;
import org.apache.oozie.util.DateUtils;
import org.apache.oozie.util.IOUtils;
import org.apache.oozie.util.XConfiguration;
import org.apache.oozie.util.XLog;
import org.apache.oozie.util.XmlUtils;
import org.apache.oozie.workflow.WorkflowApp;
import org.apache.oozie.workflow.WorkflowInstance;
import org.apache.oozie.workflow.WorkflowLib;
import org.apache.oozie.workflow.lite.EndNodeDef;
import org.apache.oozie.workflow.lite.LiteWorkflowApp;
import org.apache.oozie.workflow.lite.LiteWorkflowInstance;
import org.apache.oozie.workflow.lite.StartNodeDef;
import org.jdom.Element;
import org.jdom.JDOMException;

public abstract class XDataTestCase extends XFsTestCase {

    /**
     * Insert coord job for testing.
     *
     * @param status
     * @throws IOException
     * @throws CommandException
     * @return coord job bean
     */
    protected CoordinatorJobBean addRecordToCoordJobTable(CoordinatorJob.Status status) throws CommandException,
            IOException {
        CoordinatorJobBean coordJob = createCoordJob(status);

        try {
            JPAService jpaService = Services.get().get(JPAService.class);
            assertNotNull(jpaService);
            CoordJobInsertCommand coordInsertCmd = new CoordJobInsertCommand(coordJob);
            jpaService.execute(coordInsertCmd);
        }
        catch (CommandException ce) {
            ce.printStackTrace();
            fail("Unable to insert the test coord job record to table");
            throw ce;
        }

        return coordJob;

    }

    /**
     * Create coord job bean
     *
     * @param status
     * @param appPath
     * @param appXml
     * @return coord job bean
     * @throws IOException
     */
    protected CoordinatorJobBean createCoordJob(CoordinatorJob.Status status)
            throws IOException {
        Path appPath = new Path(getFsTestCaseDir(), "coord");
        String appXml = writeCoordXml(appPath);

        CoordinatorJobBean coordJob = new CoordinatorJobBean();
        coordJob.setId(Services.get().get(UUIDService.class).generateId(ApplicationType.COORDINATOR));
        coordJob.setAppName("COORD-TEST");
        coordJob.setAppPath(appPath.toString());
        coordJob.setStatus(status);
        coordJob.setTimeZone("America/Los_Angeles");
        coordJob.setCreatedTime(new Date());
        coordJob.setLastModifiedTime(new Date());
        coordJob.setUser(getTestUser());
        coordJob.setGroup(getTestGroup());
        coordJob.setAuthToken("notoken");

        Configuration conf = getCoordConf(appPath);
        coordJob.setConf(XmlUtils.prettyPrint(conf).toString());
        coordJob.setJobXml(appXml);
        coordJob.setLastActionNumber(0);
        coordJob.setFrequency(1);
        coordJob.setTimeUnit(Timeunit.DAY);
        coordJob.setExecution(Execution.FIFO);
        coordJob.setConcurrency(1);
        try {
            coordJob.setStartTime(DateUtils.parseDateUTC("2009-12-15T01:00Z"));
            coordJob.setEndTime(DateUtils.parseDateUTC("2009-12-17T01:00Z"));
        }
        catch (Exception e) {
            e.printStackTrace();
            fail("Could not set Date/time");
        }
        return coordJob;
    }

    /**
     * @param appPath
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    protected String writeCoordXml(Path appPath) throws IOException, UnsupportedEncodingException {
        String appXml = getCoordJobXml(appPath);

        FileSystem fs = getFileSystem();

        Writer writer = new OutputStreamWriter(fs.create(new Path(appPath + "/coordinator.xml")));
        byte[] bytes = appXml.getBytes("UTF-8");
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        Reader reader2 = new InputStreamReader(bais);
        IOUtils.copyCharStream(reader2, writer);
        return appXml;
    }


    /**
     * @param appPath
     * @return testFileName
     * @throws IOException
     * @throws UnsupportedEncodingException
     */
    protected String writeCoordXml(Path appPath, String testFileName) throws IOException, UnsupportedEncodingException {
        String appXml = getCoordJobXml(testFileName);
        writeToFile(appXml, appPath, "coordinator.xml");
        return appXml;
    }

    /**
     * Insert coord action for testing.
     *
     * @param jobId
     * @param actionNum
     * @param status
     * @param resourceXmlName
     * @return coord action bean
     * @throws IOException
     * @throws CommandException
     */
    protected CoordinatorActionBean addRecordToCoordActionTable(String jobId, int actionNum, CoordinatorAction.Status status, String resourceXmlName) throws CommandException, IOException {
        CoordinatorActionBean action = createCoordAction(jobId, actionNum, status, resourceXmlName);

        try {
            JPAService jpaService = Services.get().get(JPAService.class);
            assertNotNull(jpaService);
            CoordActionInsertCommand coordActionInsertCmd = new CoordActionInsertCommand(action);
            jpaService.execute(coordActionInsertCmd);
        }
        catch (CommandException ce) {
            ce.printStackTrace();
            fail("Unable to insert the test coord action record to table");
            throw ce;
        }
        return action;
    }

    /**
     * Create coord action bean
     *
     * @param jobId
     * @param actionNum
     * @param status
     * @param resourceXmlName
     * @return coord action bean
     * @throws IOException
     */
    protected CoordinatorActionBean createCoordAction(String jobId, int actionNum, CoordinatorAction.Status status,
            String resourceXmlName) throws IOException {
        String actionId = Services.get().get(UUIDService.class).generateChildId(jobId, actionNum + "");
        Path appPath = new Path(getFsTestCaseDir(), "coord");
        String actionXml = getCoordActionXml(appPath, resourceXmlName);
        String actionNomialTime = getActionNomialTime(actionXml);

        CoordinatorActionBean action = new CoordinatorActionBean();
        action.setId(actionId);
        action.setJobId(jobId);
        action.setActionNumber(actionNum);
        try {
            action.setNominalTime(DateUtils.parseDateUTC(actionNomialTime));
        }
        catch (Exception e) {
            e.printStackTrace();
            fail("Unable to get action nominal time");
            throw new IOException(e);
        }
        action.setLastModifiedTime(new Date());
        action.setStatus(status);
        action.setActionXml(actionXml);

        Configuration conf = getCoordConf(appPath);
        action.setCreatedConf(XmlUtils.prettyPrint(conf).toString());
        return action;
    }

    /**
     * Insert wf job for testing.
     *
     * @param jobStatus
     * @param instanceStatus
     * @return job bean
     * @throws Exception
     */
    protected WorkflowJobBean addRecordToWfJobTable(WorkflowJob.Status jobStatus, WorkflowInstance.Status instanceStatus) throws Exception {
        WorkflowApp app = new LiteWorkflowApp("testApp", "<workflow-app/>", new StartNodeDef("end"))
                .addNode(new EndNodeDef("end"));
        Configuration conf = new Configuration();
        Path appUri = new Path(getAppPath(), "workflow.xml");
        conf.set(OozieClient.APP_PATH, appUri.toString());
        conf.set(OozieClient.LOG_TOKEN, "testToken");
        conf.set(OozieClient.USER_NAME, getTestUser());
        conf.set(OozieClient.GROUP_NAME, getTestGroup());
        injectKerberosInfo(conf);
        WorkflowJobBean wfBean = createWorkflow(app, conf, "auth", jobStatus, instanceStatus);

        try {
            JPAService jpaService = Services.get().get(JPAService.class);
            assertNotNull(jpaService);
            WorkflowJobInsertCommand wfInsertCmd = new WorkflowJobInsertCommand(wfBean);
            jpaService.execute(wfInsertCmd);
        }
        catch (CommandException ce) {
            ce.printStackTrace();
            fail("Unable to insert the test wf job record to table");
            throw ce;
        }
        return wfBean;
    }

    protected Path getAppPath() {
        Path baseDir = getFsTestCaseDir();
        return new Path(baseDir, "app");
    }

    /**
     * Insert wf action for testing.
     *
     * @param wfId
     * @param actionName TODO
     * @param status
     * @return action bean
     * @throws Exception
     */
    protected WorkflowActionBean addRecordToWfActionTable(String wfId, String actionName, WorkflowAction.Status status) throws Exception {
        WorkflowActionBean action = createWorkflowAction(wfId, actionName, status);
        try {
            JPAService jpaService = Services.get().get(JPAService.class);
            assertNotNull(jpaService);
            WorkflowActionInsertCommand actionInsertCmd = new WorkflowActionInsertCommand(action);
            jpaService.execute(actionInsertCmd);
        }
        catch (CommandException ce) {
            ce.printStackTrace();
            fail("Unable to insert the test wf action record to table");
            throw ce;
        }
        return action;
    }

    /**
     * Insert sla event for testing.
     *
     * @throws Exception
     */
    protected void addRecordToSLAEventTable(String slaId) throws Exception {
        SLAEventBean sla = new SLAEventBean();
        sla.setSlaId(slaId);
        sla.setAppName("app-name");
        sla.setParentClientId("parent-child-id");
        sla.setParentSlaId("parent-sla-id");
        sla.setExpectedStart(new Date());
        sla.setExpectedEnd(new Date());
        sla.setNotificationMsg("notification-msg");
        sla.setAlertContact("alert-contact");
        sla.setDevContact("dev-contact");
        sla.setQaContact("qa-contact");
        sla.setSeContact("se-contact");
        sla.setAlertFrequency("alert-frequency");
        sla.setAlertPercentage("alert-percentage");
        sla.setUpstreamApps("upstream-apps");
        sla.setAppType(SLAEvent.SlaAppType.WORKFLOW_JOB);
        sla.setUser(getTestUser());
        sla.setGroupName(getTestGroup());
        sla.setJobStatus(Status.CREATED);
        sla.setStatusTimestamp(new Date());

        try {
            JPAService jpaService = Services.get().get(JPAService.class);
            assertNotNull(jpaService);
            SLAEventInsertCommand slaInsertCmd = new SLAEventInsertCommand(sla);
            jpaService.execute(slaInsertCmd);
        }
        catch (CommandException ce) {
            ce.printStackTrace();
            fail("Unable to insert the test sla event record to table");
            throw ce;
        }
    }

    /**
     * Insert bundle job for testing.
     *
     * @param jobStatus
     * @return bundle job bean
     * @throws Exception
     */
    protected BundleJobBean addRecordToBundleJobTable(Job.Status jobStatus) throws Exception {
        BundleJobBean bundle = createBundleJob(jobStatus);
        try {
            JPAService jpaService = Services.get().get(JPAService.class);
            assertNotNull(jpaService);
            BundleJobInsertCommand bundleInsertCmd = new BundleJobInsertCommand(bundle);
            jpaService.execute(bundleInsertCmd);
        }
        catch (CommandException ce) {
            ce.printStackTrace();
            fail("Unable to insert the test bundle job record to table");
            throw ce;
        }
        return bundle;
    }

    /**
     * Read coord job xml from test resources
     *
     * @param appPath
     * @return coord job xml
     */
    protected String getCoordJobXml(Path appPath) {
        String inputTemplate = appPath + "/coord-input/${YEAR}/${MONTH}/${DAY}/${HOUR}/${MINUTE}";
        inputTemplate = Matcher.quoteReplacement(inputTemplate);
        String outputTemplate = appPath + "/coord-input/${YEAR}/${MONTH}/${DAY}/${HOUR}/${MINUTE}";
        outputTemplate = Matcher.quoteReplacement(outputTemplate);
        try {
            Reader reader = IOUtils.getResourceAsReader("coord-rerun-job.xml", -1);
            String appXml = IOUtils.getReaderAsString(reader, -1);
            appXml = appXml.replaceAll("#inputTemplate", inputTemplate);
            appXml = appXml.replaceAll("#outputTemplate", outputTemplate);
            return appXml;
        }
        catch (IOException ioe) {
            throw new RuntimeException(XLog.format("Could not get coord-rerun-job.xml", ioe));
        }
    }

    /**
     * Read coord job xml from test resources
     * @param testFileName
     *
     * @return coord job xml
     */
    protected String getCoordJobXml(String testFileName) {
        try {
            Reader reader = IOUtils.getResourceAsReader(testFileName, -1);
            String appXml = IOUtils.getReaderAsString(reader, -1);
            return appXml;
        }
        catch (IOException ioe) {
            throw new RuntimeException(XLog.format("Could not get [{0}]", testFileName, ioe));
        }
    }

    /**
     * Read coord action xml from test resources
     *
     * @param appPath
     * @param resourceXmlName
     * @return coord action xml
     */
    protected String getCoordActionXml(Path appPath, String resourceXmlName) {
        String inputTemplate = appPath + "/coord-input/${YEAR}/${MONTH}/${DAY}/${HOUR}/${MINUTE}";
        inputTemplate = Matcher.quoteReplacement(inputTemplate);
        String outputTemplate = appPath + "/coord-input/${YEAR}/${MONTH}/${DAY}/${HOUR}/${MINUTE}";
        outputTemplate = Matcher.quoteReplacement(outputTemplate);

        String inputDir = appPath + "/coord-input/2010/07/05/01/00";
        inputDir = Matcher.quoteReplacement(inputDir);
        String outputDir = appPath + "/coord-input/2009/12/14/11/00";
        outputDir = Matcher.quoteReplacement(outputDir);
        try {
            Reader reader = IOUtils.getResourceAsReader(resourceXmlName, -1);
            String appXml = IOUtils.getReaderAsString(reader, -1);
            appXml = appXml.replaceAll("#inputTemplate", inputTemplate);
            appXml = appXml.replaceAll("#outputTemplate", outputTemplate);
            appXml = appXml.replaceAll("#inputDir", inputDir);
            appXml = appXml.replaceAll("#outputDir", outputDir);
            return appXml;
        }
        catch (IOException ioe) {
            throw new RuntimeException(XLog.format("Could not get " + resourceXmlName, ioe));
        }
    }

    protected Configuration getCoordConf(Path appPath) throws IOException {
        Path wfAppPath = new Path(getFsTestCaseDir(), "coord");

        Configuration jobConf = new XConfiguration();
        jobConf.set(OozieClient.COORDINATOR_APP_PATH, appPath.toString());
        jobConf.set(OozieClient.USER_NAME, getTestUser());
        jobConf.set(OozieClient.GROUP_NAME, getTestGroup());
        jobConf.set("jobTracker", getJobTrackerUri());
        jobConf.set("nameNode", getNameNodeUri());
        jobConf.set("wfAppPath", wfAppPath.toString());
        injectKerberosInfo(jobConf);

        String content = "<workflow-app xmlns='uri:oozie:workflow:0.1'  xmlns:sla='uri:oozie:sla:0.1' name='no-op-wf'>";
        content += "<start to='end' />";
        content += "<end name='end' /></workflow-app>";
        writeToFile(content, wfAppPath, "workflow.xml");

        return jobConf;
    }

    private void writeToFile(String content, Path appPath, String fileName) throws IOException {
        FileSystem fs = getFileSystem();
        Writer writer = new OutputStreamWriter(fs.create(new Path(appPath, fileName), true));
        writer.write(content);
        writer.close();
    }

    private String getActionNomialTime(String actionXml) {
        Element eAction;
        try {
            eAction = XmlUtils.parseXml(actionXml);
        }
        catch (JDOMException je) {
            throw new RuntimeException(XLog.format("Could not parse actionXml :" + actionXml, je));
        }
        String actionNomialTime = eAction.getAttributeValue("action-nominal-time");

        return actionNomialTime;
    }

    /**
     * Create workflow job bean
     *
     * @param app
     * @param conf
     * @param authToken
     * @param jobStatus
     * @param instanceStatus
     * @return workflow job bean
     * @throws Exception
     */
    protected WorkflowJobBean createWorkflow(WorkflowApp app, Configuration conf, String authToken,
            WorkflowJob.Status jobStatus, WorkflowInstance.Status instanceStatus) throws Exception {
        WorkflowAppService wps = Services.get().get(WorkflowAppService.class);
        Configuration protoActionConf = wps.createProtoActionConf(conf, authToken, true);
        WorkflowLib workflowLib = Services.get().get(WorkflowStoreService.class).getWorkflowLibWithNoDB();
        WorkflowInstance wfInstance = workflowLib.createInstance(app, conf);
        ((LiteWorkflowInstance) wfInstance).setStatus(instanceStatus);
        WorkflowJobBean workflow = new WorkflowJobBean();
        workflow.setId(Services.get().get(UUIDService.class).generateId(ApplicationType.WORKFLOW));
        workflow.setAppName(app.getName());
        workflow.setAppPath(conf.get(OozieClient.APP_PATH));
        workflow.setConf(XmlUtils.prettyPrint(conf).toString());
        workflow.setProtoActionConf(XmlUtils.prettyPrint(protoActionConf).toString());
        workflow.setCreatedTime(new Date());
        workflow.setLogToken(conf.get(OozieClient.LOG_TOKEN, ""));
        workflow.setStatus(jobStatus);
        workflow.setRun(0);
        workflow.setUser(conf.get(OozieClient.USER_NAME));
        workflow.setGroup(conf.get(OozieClient.GROUP_NAME));
        workflow.setAuthToken(authToken);
        workflow.setWorkflowInstance(wfInstance);
        return workflow;
    }

    /**
     * Create workflow action bean
     *
     * @param wfId
     * @param actionName TODO
     * @param status
     * @return workflow action bean
     * @throws Exception
     */
    protected WorkflowActionBean createWorkflowAction(String wfId, String actionName, WorkflowAction.Status status) throws Exception {
        WorkflowActionBean action = new WorkflowActionBean();
        action.setName(actionName);
        action.setId(Services.get().get(UUIDService.class).generateChildId(wfId, actionName));
        action.setJobId(wfId);
        action.setType("map-reduce");
        action.setTransition("transition");
        action.setStatus(status);
        action.setStartTime(new Date());
        action.setEndTime(new Date());
        action.setLastCheckTime(new Date());
        action.resetPendingOnly();

        Path inputDir = new Path(getFsTestCaseDir(), "input");
        Path outputDir = new Path(getFsTestCaseDir(), "output");

        FileSystem fs = getFileSystem();
        Writer w = new OutputStreamWriter(fs.create(new Path(inputDir, "data.txt")));
        w.write("dummy\n");
        w.write("dummy\n");
        w.close();

        String actionXml = "<map-reduce>" +
        "<job-tracker>" + getJobTrackerUri() + "</job-tracker>" +
        "<name-node>" + getNameNodeUri() + "</name-node>" +
        "<configuration>" +
        "<property><name>mapred.mapper.class</name><value>" + MapperReducerForTest.class.getName() +
        "</value></property>" +
        "<property><name>mapred.reducer.class</name><value>" + MapperReducerForTest.class.getName() +
        "</value></property>" +
        "<property><name>mapred.input.dir</name><value>"+inputDir.toString()+"</value></property>" +
        "<property><name>mapred.output.dir</name><value>"+outputDir.toString()+"</value></property>" +
        "</configuration>" +
        "</map-reduce>";
        action.setConf(actionXml);

        return action;
    }

    /**
     * Create bundle job bean
     *
     * @param jobStatus
     * @return bundle job bean
     * @throws Exception
     */
    protected BundleJobBean createBundleJob(Job.Status jobStatus) throws Exception {
        Path coordPath1 = new Path(getFsTestCaseDir(), "coord1");
        Path coordPath2 = new Path(getFsTestCaseDir(), "coord2");
        writeCoordXml(coordPath1, "coord-job-bundle.xml");
        writeCoordXml(coordPath2, "coord-job-bundle.xml");

        Path bundleAppPath = new Path(getFsTestCaseDir(), "bundle");
        String bundleAppXml = getBundleXml("bundle-submit-job.xml");

        bundleAppXml = bundleAppXml.replaceAll("#app_path1", coordPath1.toString() + File.separator + "coordinator.xml");
        bundleAppXml = bundleAppXml.replaceAll("#app_path2", coordPath2.toString() + File.separator + "coordinator.xml");
        //bundleAppXml = bundleAppXml.replaceAll("#app_path1", coordPath1.toString());
        //bundleAppXml = bundleAppXml.replaceAll("#app_path2", coordPath2.toString());

        writeToFile(bundleAppXml, bundleAppPath, "bundle.xml");

        Configuration conf = new XConfiguration();
        conf.set(OozieClient.BUNDLE_APP_PATH, bundleAppPath.toString());
        conf.set(OozieClient.USER_NAME, getTestUser());
        conf.set(OozieClient.GROUP_NAME, getTestGroup());
        conf.set("jobTracker", getJobTrackerUri());
        conf.set("nameNode", getNameNodeUri());
        injectKerberosInfo(conf);

        BundleJobBean bundle = new BundleJobBean();
        bundle.setId(Services.get().get(UUIDService.class).generateId(ApplicationType.BUNDLE));
        bundle.setAppName("BUNDLE-TEST");
        bundle.setAppPath(bundleAppPath.toString());
        bundle.setAuthToken("authToken");
        bundle.setConf(XmlUtils.prettyPrint(conf).toString());
        bundle.setConsoleUrl("consoleUrl");
        bundle.setCreatedTime(new Date());
        //TODO bundle.setStartTime(startTime);
        //TODO bundle.setEndTime(endTime);
        //TODO bundle.setExternalId(externalId);
        bundle.setJobXml(bundleAppXml);
        bundle.setLastModifiedTime(new Date());
        bundle.setOrigJobXml(bundleAppXml);
        bundle.resetPending();
        bundle.setStatus(jobStatus);
        bundle.setUser(conf.get(OozieClient.USER_NAME));
        bundle.setGroup(conf.get(OozieClient.GROUP_NAME));

        return bundle;
    }

    private String getBundleXml(String resourceXmlName) {
        try {
            Reader reader = IOUtils.getResourceAsReader(resourceXmlName, -1);
            String appXml = IOUtils.getReaderAsString(reader, -1);
            return appXml;
        }
        catch (IOException ioe) {
            throw new RuntimeException(XLog.format("Could not get " + resourceXmlName, ioe));
        }
    }

}