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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.WsdlTestSuite;
import com.eviware.soapui.impl.wsdl.testcase.WsdlProjectRunner;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCase;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestCaseRunner;
import com.eviware.soapui.impl.wsdl.testcase.WsdlTestSuiteRunner;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestStep;
import com.eviware.soapui.impl.wsdl.teststeps.WsdlTestStepResult;
import com.eviware.soapui.model.project.ProjectFactoryRegistry;
import com.eviware.soapui.model.support.ProjectRunListenerAdapter;
import com.eviware.soapui.model.testsuite.*;
import com.eviware.soapui.model.testsuite.Assertable.AssertionStatus;
import com.eviware.soapui.model.testsuite.AssertionError;
import com.eviware.soapui.model.testsuite.TestRunner.Status;
import com.eviware.soapui.report.JUnitReportCollector;
import com.eviware.soapui.support.types.StringToObjectMap;
import com.eviware.soapui.tools.AbstractSoapUITestRunner;

import org.apache.commons.cli.CommandLine;

import de.interactive_instruments.etf.sel.mapping.CollectorInjectionAdapter;
import de.interactive_instruments.etf.sel.mapping.ProjectRunCollector;
import de.interactive_instruments.etf.sel.mapping.TestRunCollector;
import de.interactive_instruments.etf.testdriver.TestResultCollector;

/**
 * SoapUI Test Case Runner
 *
 * @author ole.matzura
 */

class STestCaseRunner extends AbstractSoapUITestRunner {
	public static final String SOAPUI_EXPORT_SEPARATOR = "soapui.export.separator";

	// public static final String TITLE = "soapUI " + SoapUI.SOAPUI_VERSION + " TestCase Runner";
	public static final String TITLE = "";
	private final SuiTestTaskProgress progress;

	private String testSuite;
	private String testCase;
	private List<TestAssertion> assertions = new ArrayList<TestAssertion>();
	private Map<TestAssertion, WsdlTestStepResult> assertionResults = new HashMap<TestAssertion, WsdlTestStepResult>();
	// private List<TestCaseRunner> runningTests = new
	// ArrayList<TestCaseRunner>();
	private List<TestCase> failedTests = new ArrayList<TestCase>();

	private int testSuiteCount;
	private int testCaseCount;
	private int testStepCount;
	private int testAssertionCount;

	// private boolean exportAll;
	private boolean ignoreErrors;
	private boolean junitReport;
	private int exportCount;
	private int maxErrors = 5;
	private JUnitReportCollector reportCollector;
	// private WsdlProject project;
	private String projectPassword;
	private boolean saveAfterRun;
	private boolean failed;

	// private final SuiTestRunnerProgress progress;

	private WsdlProject project;
	private WsdlProjectRunner runner = null;

	public boolean isFailed() {
		return failed;
	}

	protected boolean processCommandLine(CommandLine cmd) {
		return true;
	}

	public void setMaxErrors(int maxErrors) {
		this.maxErrors = maxErrors;
	}

	protected int getMaxErrors() {
		return maxErrors;
	}

	public void setSaveAfterRun(boolean saveAfterRun) {
		this.saveAfterRun = saveAfterRun;
	}

	public void setProjectPassword(String projectPassword) {
		this.projectPassword = projectPassword;
	}

	public String getProjectPassword() {
		return projectPassword;
	}

	protected JUnitReportCollector createJUnitReportCollector() {
		return new JUnitReportCollector(maxErrors);
	}

	public STestCaseRunner(final SuiTestTaskProgress progress) {
		super(STestCaseRunner.TITLE);
		this.failed = false;
		this.progress = progress;
		// this.log.addAppender(progress.getAppender());

	}

	public void setIgnoreError(boolean ignoreErrors) {
		this.ignoreErrors = ignoreErrors;
	}

	public WsdlProject initProject(final TestResultCollector collector) throws Exception {

		// Does not work with the log4j-over-slf4j bridge, as setWriter() method is not supported
		// initGroovyLog();

		assertions.clear();

		String projectFile = getProjectFile();
		// project = (WsdlProject) ProjectFactoryRegistry.getProjectFactory("wsdl");
		// project.loadProject(new URL(projectFile));

		this.project = (WsdlProject) ProjectFactoryRegistry.getProjectFactory("wsdl").createNew(projectFile,
				getProjectPassword());

		if (project.isDisabled()) {
			throw new Exception("Failed to load soapUI project file [" + projectFile + "]");
		}

		this.project.addProjectListener(this.progress);
		project.setActiveEnvironment(new CollectorInjectionAdapter(project, collector));
		this.project.addProjectRunListener(new ProjectRunCollector(collector));

		initProject(project);
		ensureOutputFolder(project);

		int tsCount = 0;
		for (final TestSuite ts : project.getTestSuiteList()) {
			ts.addTestSuiteListener(this.progress);
			for (final TestCase tc : ts.getTestCaseList()) {
				tsCount += tc.getTestStepCount();
				tc.addTestRunListener(new TestRunCollector(collector));
			}
		}

		return project;
	}

	public boolean runRunner() throws Exception {

		// catch org.codehaus.groovy.syntax.SyntaxException
		log.info("Running soapUI tests in project [" + project.getName() + "]");

		long startTime = System.nanoTime();

		List<TestCase> testCasesToRun = new ArrayList<TestCase>();

		// start by listening to all testcases.. (since one testcase can call
		// another)
		for (int c = 0; c < project.getTestSuiteCount(); c++) {
			TestSuite suite = project.getTestSuiteAt(c);
			for (int i = 0; i < suite.getTestCaseCount(); i++) {
				TestCase tc = suite.getTestCaseAt(i);
				if ((testSuite == null || suite.getName().equals(suite.getName())) && testCase != null
						&& tc.getName().equals(testCase))
					testCasesToRun.add(tc);

				addListeners(tc);
			}
		}

		String[] projectProperties = getProjectProperties();
		for (int i = 0; i < projectProperties.length - 1; i++) {
			project.setPropertyValue(projectProperties[i], projectProperties[++i]);
		}

		// decide what to run
		if (testCasesToRun.size() > 0) {
			for (TestCase testCase : testCasesToRun)
				runTestCase((WsdlTestCase) testCase);
		} else if (testSuite != null) {
			WsdlTestSuite ts = project.getTestSuiteByName(testSuite);
			if (ts == null)
				throw new Exception("TestSuite with name [" + testSuite + "] not found in project");
			else
				runSuite(ts);
		} else {
			runProject(project);
		}

		long timeTaken = (System.nanoTime() - startTime) / 1000000;

		exportReports(project);

		if (saveAfterRun && !project.isRemote()) {
			try {
				project.save();
			} catch (Throwable t) {
				log.error("Failed to save project", t);
			}
		}

		if ((assertions.size() > 0 || failedTests.size() > 0) && !ignoreErrors) {
			// throwFailureException();
			return false;
		}

		return true;
	}

	public void cancel() {
		if (runner != null) {
			runner.cancel("User requested termination");
		}
	}

	protected void runProject(WsdlProject project) {
		// add listener for counting..
		InternalProjectRunListener projectRunListener = new InternalProjectRunListener();
		project.addProjectRunListener(projectRunListener);

		try {
			log.info(("Running Project [" + project.getName() + "], runType = " + project.getRunType()));
			runner = project.run(new StringToObjectMap(), true);
			runner.waitUntilFinished();
			log.info("Project [" + project.getName() + "] finished with status [" + runner.getStatus() + "] in "
					+ runner.getTimeTaken() + "ms");
		} catch (Exception e) {
			this.failed = true;
		} finally {
			project.removeProjectRunListener(projectRunListener);
		}
	}

	protected void initProject(WsdlProject project) throws Exception {
		initProjectProperties(project);
	}

	protected void exportReports(WsdlProject project) throws Exception {
		if (junitReport) {
			exportJUnitReports(reportCollector, getAbsoluteOutputFolder(project), project);
		}
	}

	protected void addListeners(TestCase tc) {
		tc.addTestRunListener(this);
		if (junitReport)
			tc.addTestRunListener(reportCollector);
	}

	protected void throwFailureException() throws Exception {
		StringBuffer buf = new StringBuffer();

		for (int c = 0; c < assertions.size(); c++) {
			TestAssertion assertion = assertions.get(c);
			Assertable assertable = assertion.getAssertable();
			if (assertable instanceof WsdlTestStep)
				failedTests.remove(((WsdlTestStep) assertable).getTestCase());

			buf.append(assertion.getName() + " in [" + assertable.getModelItem().getName() + "] failed;\n");
			buf.append(Arrays.toString(assertion.getErrors()) + "\n");

			WsdlTestStepResult result = assertionResults.get(assertion);
			StringWriter stringWriter = new StringWriter();
			PrintWriter writer = new PrintWriter(stringWriter);
			result.writeTo(writer);
			buf.append(stringWriter.toString());
		}

		while (!failedTests.isEmpty()) {
			buf.append("TestCase [" + failedTests.remove(0).getName() + "] failed without assertions\n");
		}

		throw new Exception(buf.toString());
	}

	public void exportJUnitReports(JUnitReportCollector collector, String folder, WsdlProject project)
			throws Exception {
		collector.saveReports(folder == null ? "" : folder);
	}

	/**
	 * Run tests in the specified TestSuite
	 *
	 * @param suite
	 *           the TestSuite to run
	 */

	protected void runSuite(WsdlTestSuite suite) {
		try {
			log.info(("Running TestSuite [" + suite.getName() + "], runType = " + suite.getRunType()));
			WsdlTestSuiteRunner runner = suite.run(new StringToObjectMap(), false);
			log.info("TestSuite [" + suite.getName() + "] finished with status [" + runner.getStatus() + "] in "
					+ (runner.getTimeTaken()) + "ms");
		} catch (Exception e) {
			// e.printStackTrace();
			this.failed = true;
		} finally {
			testSuiteCount++;
		}
	}

	/**
	 * Runs the specified TestCase
	 *
	 * @param testCase
	 *           the testcase to run
	 */

	protected void runTestCase(WsdlTestCase testCase) {
		try {
			log.info("Running TestCase [" + testCase.getName() + "]");
			WsdlTestCaseRunner runner = testCase.run(new StringToObjectMap(), false);
			log.info("TestCase [" + testCase.getName() + "] finished with status [" + runner.getStatus() + "] in "
					+ (runner.getTimeTaken()) + "ms");
		} catch (Exception e) {
			// e.printStackTrace();
			this.failed = true;
		}
	}

	/**
	 * Sets the testcase to run
	 *
	 * @param testCase
	 *           the testcase to run
	 */

	public void setTestCase(String testCase) {
		this.testCase = testCase;
	}

	/**
	 * Sets the TestSuite to run. If not set all TestSuites in the specified
	 * project file are run
	 *
	 * @param testSuite
	 *           the testSuite to run.
	 */

	public void setTestSuite(String testSuite) {
		this.testSuite = testSuite;
	}

	public void beforeRun(TestCaseRunner testRunner, TestCaseRunContext runContext) {
		log.info("Running soapUI testcase [" + testRunner.getTestCase().getName() + "]");
	}

	public void beforeStep(TestCaseRunner testRunner, TestCaseRunContext runContext, TestStep currentStep) {
		super.beforeStep(testRunner, runContext, currentStep);

		if (currentStep != null)
			log.info("running step [" + currentStep.getName() + "]");
	}

	public void afterStep(TestCaseRunner testRunner, TestCaseRunContext runContext, TestStepResult result) {
		super.afterStep(testRunner, runContext, result);
		TestStep currentStep = runContext.getCurrentStep();

		if (currentStep instanceof Assertable) {
			Assertable requestStep = (Assertable) currentStep;
			for (int c = 0; c < requestStep.getAssertionCount(); c++) {
				TestAssertion assertion = requestStep.getAssertionAt(c);
				log.info("Assertion [" + assertion.getName() + "] has status " + assertion.getStatus());
				if (assertion.getStatus() == AssertionStatus.FAILED) {
					for (AssertionError error : assertion.getErrors())
						log.error("ASSERTION FAILED -> " + error.getMessage());

					assertions.add(assertion);
					assertionResults.put(assertion, (WsdlTestStepResult) result);
				}

				testAssertionCount++;
			}
		}

		String countPropertyName = currentStep.getName() + " run count";
		Long count = (Long) runContext.getProperty(countPropertyName);
		if (count == null) {
			count = new Long(0);
		}

		runContext.setProperty(countPropertyName, new Long(count.longValue() + 1));

		testStepCount++;
	}

	public void afterRun(TestCaseRunner testRunner, TestCaseRunContext runContext) {
		log.info("Finished running soapUI testcase [" + testRunner.getTestCase().getName() + "], time taken: "
				+ testRunner.getTimeTaken() + "ms, status: " + testRunner.getStatus());

		if (testRunner.getStatus() == Status.FAILED) {
			failedTests.add(testRunner.getTestCase());
		}

		testCaseCount++;
	}

	private class InternalProjectRunListener extends ProjectRunListenerAdapter {
		public void afterTestSuite(ProjectRunner projectRunner, ProjectRunContext runContext, TestSuiteRunner testRunner) {
			testSuiteCount++;
		}
	}

	@Override
	protected SoapUIOptions initCommandLineOptions() {
		return null;
	}
}
