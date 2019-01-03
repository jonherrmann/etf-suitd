/**
 * Copyright 2017-2019 European Union, interactive instruments GmbH
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 * This work was supported by the EU Interoperability Solutions for
 * European Public Administrations Programme (http://ec.europa.eu/isa)
 * through Action 1.17: A Reusable INSPIRE Reference Platform (ARE3NA).
 */
package de.interactive_instruments.etf.testdriver.sui;

import static de.interactive_instruments.etf.testdriver.sui.SuiTestDriver.SUI_TEST_DRIVER_EID;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;

import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.interactive_instruments.IFile;
import de.interactive_instruments.SUtils;
import de.interactive_instruments.etf.EtfConstants;
import de.interactive_instruments.etf.component.ComponentLoadingException;
import de.interactive_instruments.etf.component.ComponentNotLoadedException;
import de.interactive_instruments.etf.dal.dao.DataStorage;
import de.interactive_instruments.etf.dal.dao.DataStorageRegistry;
import de.interactive_instruments.etf.dal.dao.WriteDao;
import de.interactive_instruments.etf.dal.dto.capabilities.ResourceDto;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectDto;
import de.interactive_instruments.etf.dal.dto.capabilities.TestObjectTypeDto;
import de.interactive_instruments.etf.dal.dto.result.TestTaskResultDto;
import de.interactive_instruments.etf.dal.dto.run.TestRunDto;
import de.interactive_instruments.etf.dal.dto.run.TestTaskDto;
import de.interactive_instruments.etf.dal.dto.test.ExecutableTestSuiteDto;
import de.interactive_instruments.etf.model.EidFactory;
import de.interactive_instruments.etf.test.DataStorageTestUtils;
import de.interactive_instruments.etf.testdriver.DefaultTestDriverManager;
import de.interactive_instruments.etf.testdriver.TaskPoolRegistry;
import de.interactive_instruments.etf.testdriver.TestDriverManager;
import de.interactive_instruments.etf.testdriver.TestRun;
import de.interactive_instruments.exceptions.*;
import de.interactive_instruments.exceptions.config.ConfigurationException;
import de.interactive_instruments.properties.PropertyUtils;

/**
 *
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
public class SuiTestRunTaskFactoryTest {

	// DO NOT RUN THE TESTS IN THE IDE BUT WITH GRADLE

	private static TestDriverManager testDriverManager = null;
	private static IFile testProjectDir = null;
	private static DataStorage DATA_STORAGE = DataStorageTestUtils.inMemoryStorage();

	private TestRunDto createTestRunDtoForProject(final String url)
			throws ComponentNotLoadedException, ConfigurationException, URISyntaxException,
			ObjectWithIdNotFoundException, IOException {

		final TestObjectDto testObjectDto = new TestObjectDto();
		testObjectDto.setId(EidFactory.getDefault().createAndPreserveStr("fcfe9677-7b77-41dd-a17c-56884f60824f"));
		testObjectDto.setLabel("Cite 2013 WFS");
		final TestObjectTypeDto wfsTestObjecType = DATA_STORAGE.getDao(TestObjectTypeDto.class).getById(
				EidFactory.getDefault().createAndPreserveStr("9b6ef734-981e-4d60-aa81-d6730a1c6389")).getDto();
		testObjectDto.setTestObjectType(wfsTestObjecType);
		testObjectDto.addResource(new ResourceDto("serviceEndpoint", url));
		testObjectDto.setDescription("none");
		testObjectDto.setVersionFromStr("1.0.0");
		testObjectDto.setCreationDate(new Date(0));
		testObjectDto.setAuthor("ii");
		testObjectDto.setItemHash(SUtils.fastCalcHashAsHexStr(testObjectDto.getLabel()));
		testObjectDto.setLocalPath("/none");
		testObjectDto.setRemoteResource(new URI("http://nosource"));
		try {
			((WriteDao) DATA_STORAGE.getDao(TestObjectDto.class)).delete(testObjectDto.getId());
		} catch (Exception e) {
			ExcUtils.suppress(e);
		}
		((WriteDao) DATA_STORAGE.getDao(TestObjectDto.class)).add(testObjectDto);

		final ExecutableTestSuiteDto ets = DATA_STORAGE.getDao(ExecutableTestSuiteDto.class).getById(
				EidFactory.getDefault().createAndPreserveStr("d6907855-7e33-42d7-83f2-647780a6cfed")).getDto();

		final TestTaskDto testTaskDto = new TestTaskDto();
		testTaskDto.setId(EidFactory.getDefault().createAndPreserveStr("aa03825a-2f64-4e52-bdba-90a08adb80ce"));
		testTaskDto.setExecutableTestSuite(ets);
		testTaskDto.setTestObject(testObjectDto);

		final TestRunDto testRunDto = new TestRunDto();
		testRunDto.setDefaultLang("en");
		testRunDto.setId(EidFactory.getDefault().createAndPreserveStr(SUI_TEST_DRIVER_EID));
		testRunDto.setLabel("Run label");
		testRunDto.setStartTimestamp(new Date(0));
		testRunDto.addTestTask(testTaskDto);

		return testRunDto;
	}

	@BeforeClass
	public static void setUp()
			throws IOException, ConfigurationException, InvalidStateTransitionException,
			InitializationException, ObjectWithIdNotFoundException {

		// DO NOT RUN THE TESTS IN THE IDE BUT WITH GRADLE

		// Init logger
		LoggerFactory.getLogger(SuiTestRunTaskFactoryTest.class).info("Started");

		if (DataStorageRegistry.instance().get(DATA_STORAGE.getClass().getName()) == null) {
			DataStorageRegistry.instance().register(DATA_STORAGE);
		}

		if (testDriverManager == null) {

			final IFile tdDir = new IFile(PropertyUtils.getenvOrProperty(
					"ETF_TD_DEPLOYMENT_DIR", "./build/tmp/td"));
			tdDir.mkdirs();
			tdDir.expectDirIsReadable();

			testProjectDir = new IFile(PropertyUtils.getenvOrProperty(
					"ETF_TESTING_SUI_TP_DIR", "./build/tmp/testProjects"));
			testProjectDir.mkdirs();
			testProjectDir.expectDirIsReadable();

			// Load driver
			testDriverManager = new DefaultTestDriverManager();
			testDriverManager.getConfigurationProperties().setProperty(
					EtfConstants.ETF_PROJECTS_DIR, testProjectDir.getAbsolutePath());
			testDriverManager.getConfigurationProperties().setProperty(
					EtfConstants.ETF_TESTDRIVERS_DIR, tdDir.getAbsolutePath());
			final IFile attachmentDir = new IFile(PropertyUtils.getenvOrProperty(
					"ETF_DS_DIR", "./build/tmp/etf-ds")).secureExpandPathDown("attachments");
			attachmentDir.deleteDirectory();
			attachmentDir.mkdirs();
			testDriverManager.getConfigurationProperties().setProperty(
					EtfConstants.ETF_ATTACHMENT_DIR, attachmentDir.getAbsolutePath());
			testDriverManager.getConfigurationProperties().setProperty(
					EtfConstants.ETF_DATA_STORAGE_NAME,
					DATA_STORAGE.getClass().getName());

			testDriverManager.init();
			testDriverManager.load(EidFactory.getDefault().createAndPreserveStr("4838e01b-4186-4d2d-a93a-414b9e9a49a7"));
		}
	}

	@Test
	public void simpleDemoWfs2ReportTest() throws Exception, ComponentNotLoadedException {

		// DO NOT RUN THE TESTS IN THE IDE BUT DIRECTLY WITH GRADLE

		// http://www.opengeospatial.org/resource/products/compliant
		final String testUrl = "https://services.interactive-instruments.de/cite-xs-46/simpledemo/cgi-bin/cities-postgresql/wfs?request=GetCapabilities&service=wfs";

		final TestRunDto testRunDto = createTestRunDtoForProject(testUrl);

		final TestRun testRun = testDriverManager.createTestRun(testRunDto);
		final TaskPoolRegistry<TestRunDto, TestRun> taskPoolRegistry = new TaskPoolRegistry<>(1, 1);
		testRun.init();
		taskPoolRegistry.submitTask(testRun);

		final TestRunDto runResult = taskPoolRegistry.getTaskById(testRunDto.getId()).waitForResult();

		assertNotNull(runResult);
		assertNotNull(runResult.getTestTaskResults());
		assertFalse(runResult.getTestTaskResults().isEmpty());

		final TestTaskResultDto result = runResult.getTestTaskResults().get(0);
		assertNotNull(result);
		assertNotNull(result.getTestModuleResults());
		assertNotNull(result.getTestModuleResults().get(0).getResultedFrom());

		// Check correct order
		assertEquals("Initialization and basic checks", result.getTestModuleResults().get(0).getResultedFrom().getLabel());
		assertEquals("A - TS 2", result.getTestModuleResults().get(1).getResultedFrom().getLabel());
		assertEquals("Z - TS 3", result.getTestModuleResults().get(2).getResultedFrom().getLabel());
		assertEquals("B - TS 4", result.getTestModuleResults().get(3).getResultedFrom().getLabel());

		// Check order of generated TestCases
		assertNotNull(result.getTestModuleResults().get(3).getTestCaseResults().get(0));
		assertNotNull(result.getTestModuleResults().get(3).getTestCaseResults().get(0).getTestStepResults().get(0));
		assertEquals("Groovy Script", result.getTestModuleResults().get(3).getTestCaseResults().get(0).getTestStepResults()
				.get(0).getResultedFrom().getLabel());
	}

}
