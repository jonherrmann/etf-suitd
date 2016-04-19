/**
 * Copyright 2010-2016 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.interactive_instruments.etf.testrunner.soapui;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.interactive_instruments.IFile;
import de.interactive_instruments.concurrent.InvalidStateTransitionException;
import de.interactive_instruments.concurrent.TaskPoolRegistry;
import de.interactive_instruments.container.CLenFileFactory;
import de.interactive_instruments.etf.EtfConstants;
import de.interactive_instruments.etf.component.ComponentLoadingException;
import de.interactive_instruments.etf.component.ComponentNotLoadedException;
import de.interactive_instruments.etf.dal.dto.item.VersionDataDto;
import de.interactive_instruments.etf.dal.dto.plan.*;
import de.interactive_instruments.etf.dal.dto.result.TestReportDto;
import de.interactive_instruments.etf.dal.dto.result.TestReportDtoBuilder;
import de.interactive_instruments.etf.driver.*;
import de.interactive_instruments.etf.model.item.EID;
import de.interactive_instruments.etf.model.item.EidFactory;
import de.interactive_instruments.etf.model.plan.DefaultTestObjectResource;
import de.interactive_instruments.etf.model.plan.TestObject;
import de.interactive_instruments.etf.model.result.TestCaseResult;
import de.interactive_instruments.etf.model.result.TestReport;
import de.interactive_instruments.etf.model.result.TestResultStatus;
import de.interactive_instruments.etf.model.result.TestStepResult;
import de.interactive_instruments.exceptions.InitializationException;
import de.interactive_instruments.exceptions.ObjectWithIdNotFoundException;
import de.interactive_instruments.exceptions.StoreException;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.properties.Properties;
import de.interactive_instruments.properties.PropertyHolder;

/**
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
public class SuiTestRunTaskFactoryTest {

	private static int testRunCount = 0;

	private TestDriverLoader loader = null;
	private TestRunTaskFactory factory = null;
	private CLenFileFactory clenFactory = null;
	private IFile testProjectDir = null;
	private IFile reportingDir = null;

	private TestRunDto createTestRunDtoForProject(final File projFile, final String url)
			throws ComponentNotLoadedException, ConfigurationException, URISyntaxException,
			StoreException, ObjectWithIdNotFoundException, IOException {

		final String testRunCountStr = String.valueOf(++testRunCount);

		final PropertyHolder properties = new Properties();

		final TestObjectDto testObjectDto = new TestObjectDtoBuilder().setId(EidFactory.getDefault().createFromStrAsStr(url)).setItemHash(new byte[0]).setLabel("testobjLabel_" + testRunCountStr).setDescription("testobjDescription_" + testRunCountStr).setType(TestObject.TestObjectType.EXECUTABLE).setVersion(new VersionDataDto()).addResource(new DefaultTestObjectResource("serviceEndpoint", new URL(url).toURI())).setProperties(properties).createTestObjectDto();

		final TestReportDto testReportDto = new TestReportDtoBuilder().setAppendixLocation(reportingDir.toURI()).setDuration(12345).setGenerator("JUNIT").setId(EidFactory.getDefault().createFromStrAsUUID("TEST TEST REPORT_" + testRunCountStr)).setLabel("TEST TEST REPORT").setMachineName("NO MACHINE").setPublicationLocation(reportingDir.toURI()).setTestingTool("JUNIT").setTestObject(testObjectDto).setResultStatus(TestResultStatus.FAILED).createTestReportDto();

		final TestProjectDto testProjectDto = loader.getFactoryById("SUI").getTestProjectStore()
				.getDtoById(EidFactory.getDefault().createFromStrAsUUID(projFile.getAbsolutePath()));
		assertNotNull(testProjectDto);

		final EID testRunId = EidFactory.getDefault().createFromStrAsStr(testRunCountStr);
		final TestRunDto testRunDto = new TestRunDtoBuilder().setTestDriverId("SUI").setTestObject(testObjectDto).setTestReport(testReportDto).setTestProject(testProjectDto).setFactory(clenFactory).setId(testRunId).setLabel("testRunLabel").setProperties(properties).setDescription("Description").setUsername("username").setVersion(new VersionDataDto()).createTestRunDto();
		return testRunDto;
	}

	@Before
	public void setUp()
			throws IOException, ConfigurationException, InvalidStateTransitionException,
			InitializationException, ComponentLoadingException {

		if (loader == null) {

			assertNotNull(System.getenv("ETF_TD_DEPLOYMENT_DIR"));
			assertNotNull(System.getenv("ETF_TESTING_SUI_TP_DIR"));
			assertNotNull(System.getenv("ETF_TESTING_SUI_TR_DIR"));
			assertNotNull(System.getenv("ETF_TESTING_SUI_TR_DIR"));

			final IFile tdDir = new IFile(System.getenv("ETF_TD_DEPLOYMENT_DIR"));
			tdDir.expectDirIsReadable();

			testProjectDir = new IFile(System.getenv("ETF_TESTING_SUI_TP_DIR"));
			testProjectDir.expectDirIsReadable();

			reportingDir = new IFile(System.getenv("ETF_TESTING_SUI_TR_DIR"));
			reportingDir.mkdirs();

			clenFactory = new CLenFileFactory();
			clenFactory.getConfigurationProperties().setProperty(EtfConstants.ETF_APPENDICES_DIR,
					reportingDir.expandPath("appendices").getAbsolutePath());
			clenFactory.init();

			// Load driver
			loader = new TestDriverLoader(tdDir);
			final Properties configProperties = new Properties();
			configProperties
					.setProperty(SuiConstants.SUI_PROJECT_DIR_KEY, testProjectDir.getAbsolutePath());
			configProperties.setProperty(EtfConstants.ETF_TESTDRIVERS_DIR, tdDir.getAbsolutePath());
			loader.setConfig(configProperties);
			loader.load("SUI");
		}

	}

	@After
	public void tearDown() throws Exception {
		loader.release();
		assertEquals(Collections.EMPTY_MAP, loader.getFactories());
	}

	@Test
	public void simpleDemoWfs2ReportTest() throws Exception, ComponentNotLoadedException {
		final String testUrl = "http://services.interactive-instruments.de/"
				+ "cite2013wfs/simpledemo/cgi-bin/cities-postgresql/wfs?request=GetCapabilities&service=wfs";

		final IFile projFile = testProjectDir.expandPath("sui/Simple-Demo-WFS/Simple-Demo-WFS-2-soapui-project.xml");
		projFile.expectFileIsReadable();

		final TestRunDto testRunDto = createTestRunDtoForProject(projFile, testUrl);

		final TestRunTaskFactory factory = loader.getFactoryById("SUI");
		final TestRunTask task = factory.createTestRunTask(testRunDto);

		final TaskPoolRegistry<TestReport> taskPoolRegistry = new TaskPoolRegistry<TestReport>(1, 1);

		taskPoolRegistry.submitTask(task);

		final TestReport report = taskPoolRegistry.getTaskById(testRunDto.getId().toUuid()).getTaskProgress().waitForResult();

		assertNotNull(report);
		assertNotNull(report.getTestSuiteResults());
		// Check correct order
		assertEquals("TestSetup", report.getTestSuiteResults().get(0).getLabel());
		assertEquals("A - TS 2", report.getTestSuiteResults().get(1).getLabel());
		assertEquals("Z - TS 3", report.getTestSuiteResults().get(2).getLabel());
		assertEquals("B - TS 4", report.getTestSuiteResults().get(3).getLabel());

		// Check order of generated TestCases
		assertNotNull(report.getTestSuiteResults().get(3).getTestCaseResults().get(0));
		assertNotNull(report.getTestSuiteResults().get(3).getTestCaseResults().get(0).getTestStepResults().get(0));
		assertEquals("Groovy Script", report.getTestSuiteResults().get(3).getTestCaseResults().get(0).getTestStepResults().get(0).getAssociatedTestStep().getLabel());

		assertNotNull(report.getTestSuiteResults().get(3).getTestCaseResults().get(1));
		assertNotNull(report.getTestSuiteResults().get(3).getTestCaseResults().get(1).getTestStepResults().get(0));
		assertEquals("HTTP Request 1", report.getTestSuiteResults().get(3).getTestCaseResults().get(1).getTestStepResults().get(0).getAssociatedTestStep().getLabel());
	}

}
