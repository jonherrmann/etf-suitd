package com.eviware.soapui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import com.eviware.soapui.model.testsuite.Assertable;
import com.eviware.soapui.model.testsuite.Assertable.AssertionStatus;
import com.eviware.soapui.model.testsuite.AssertionError;
import com.eviware.soapui.model.testsuite.ProjectRunContext;
import com.eviware.soapui.model.testsuite.ProjectRunner;
import com.eviware.soapui.model.testsuite.TestAssertion;
import com.eviware.soapui.model.testsuite.TestCase;
import com.eviware.soapui.model.testsuite.TestCaseRunContext;
import com.eviware.soapui.model.testsuite.TestCaseRunner;
import com.eviware.soapui.model.testsuite.TestRunner.Status;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.model.testsuite.TestStepResult;
import com.eviware.soapui.model.testsuite.TestSuite;
import com.eviware.soapui.model.testsuite.TestSuiteRunner;
import com.eviware.soapui.report.JUnitReportCollector;
import com.eviware.soapui.support.types.StringToObjectMap;
import com.eviware.soapui.tools.AbstractSoapUITestRunner;

import de.interactive_instruments.etf.testrunner.soapui.SuiTestRunnerProgress;
import org.apache.commons.cli.CommandLine;


/**
 * SoapUI Test Case Runner
 *
 * @author ole.matzura (Smartbear)
 */

public class STestCaseRunner extends AbstractSoapUITestRunner {
	public static final String SOAPUI_EXPORT_SEPARATOR = "soapui.export.separator";

	// public static final String TITLE = "soapUI " + SoapUI.SOAPUI_VERSION + " TestCase Runner";
	public static final String TITLE = "";

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

	private final SuiTestRunnerProgress progress;

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

	public STestCaseRunner(SuiTestRunnerProgress progress) {
		super(STestCaseRunner.TITLE);
		this.failed = false;
		this.progress = progress;
		this.log.addAppender(progress.getAppender());

	}

	public STestCaseRunner(SuiTestRunnerProgress progress, String title) {
		super(title);
		this.failed = false;
		this.progress = progress;
		this.log.addAppender(progress.getAppender());
	}

	public void setIgnoreError(boolean ignoreErrors) {
		this.ignoreErrors = ignoreErrors;
	}

	public WsdlProject initProject() throws Exception {

		initGroovyLog();

		assertions.clear();

		String projectFile = getProjectFile();
		// project = (WsdlProject) ProjectFactoryRegistry.getProjectFactory("wsdl");
		// project.loadProject(new URL(projectFile));
		this.project = (WsdlProject) ProjectFactoryRegistry.getProjectFactory("wsdl").createNew(projectFile,
				getProjectPassword());

		if (project.isDisabled())
			throw new Exception("Failed to load soapUI project file [" + projectFile + "]");

		this.project.addProjectListener(this.progress);

		initProject(project);
		ensureOutputFolder(project);

		int tsCount = 0;
		for (final TestSuite ts : project.getTestSuiteList()) {
			ts.addTestSuiteListener(this.progress);
			for (final TestCase tc : ts.getTestCaseList()) {
				tsCount += tc.getTestStepCount();
			}
		}
		progress.setMaxSteps(tsCount);

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
			// e.printStackTrace();
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
		progress.advanceStep();
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

		/*
		if( result.getStatus() == TestStepStatus.FAILED ||exportAll )
		{
			try
			{
				String exportSeparator = System.getProperty( SOAPUI_EXPORT_SEPARATOR, "-" );

				TestCase tc = currentStep.getTestCase();
				String nameBase = StringUtils.createFileName( tc.getTestSuite().getName(), '_' ) +exportSeparator
						+ StringUtils.createFileName( tc.getName(), '_' ) +exportSeparator
						+ StringUtils.createFileName( currentStep.getName(), '_' ) + "-" + count.longValue() + "-"
						+ result.getStatus();

				WsdlTestCaseRunner callingTestCaseRunner = ( WsdlTestCaseRunner )runContext
						.getProperty( "#CallingTestCaseRunner#" );

				if( callingTestCaseRunner != null )
				{
					WsdlTestCase ctc = callingTestCaseRunner.getTestCase();
					WsdlRunTestCaseTestStep runTestCaseTestStep = ( WsdlRunTestCaseTestStep )runContext
							.getProperty( "#CallingRunTestCaseStep#" );

					nameBase = StringUtils.createFileName( ctc.getTestSuite().getName(), '_' ) +exportSeparator
							+ StringUtils.createFileName( ctc.getName(), '_' ) +exportSeparator
							+ StringUtils.createFileName( runTestCaseTestStep.getName(), '_' ) +exportSeparator
							+ StringUtils.createFileName( tc.getTestSuite().getName(), '_' ) +exportSeparator
							+ StringUtils.createFileName( tc.getName(), '_' ) +exportSeparator
							+ StringUtils.createFileName( currentStep.getName(), '_' ) + "-" + count.longValue() + "-"
							+ result.getStatus();
				}

				nameBase+=".txt";
				String absoluteOutputFolder = getAbsoluteOutputFolder( ModelSupport.getModelItemProject( tc ) );
				String fileName = absoluteOutputFolder + File.separator + nameBase;

				if( result.getStatus() == TestStepStatus.FAILED )
					log.error( currentStep.getName() + " failed,exportting to [" + fileName + "]" );

				new File( fileName ).getParentFile().mkdirs();

				PrintWriter writer = new PrintWriter( fileName );
				result.writeTo( writer );
				// TODO
				// OutputFormatter.getInstance().appendTestStepResult((WsdlTestStepResult)result, currentStep, nameBase);
				writer.close();


				TestStep calledTestCaseTestStep = (TestStep) runContext.getProperty("CalledRunTestCaseStep");
				TestStepResult calledTestCaseTestStepResult =
					(TestStepResult) runContext.getProperty("CalledTestCaseTestStepResult");

				if(calledTestCaseTestStep!=null && calledTestCaseTestStepResult!=null) {
						nameBase ="RTS_"+
						 StringUtils.createFileName(calledTestCaseTestStep.getTestCase().getTestSuite().
							 getName(), (char)'_')+"_"+
						StringUtils.createFileName(calledTestCaseTestStep.getTestCase().getName(),
							(char)'_')+"_"+
						StringUtils.createFileName(currentStep.getName(),
							(char)'_')+"___"+
						StringUtils.createFileName(calledTestCaseTestStep.getName(), (char)'_')+
						 "-FAILED";

						nameBase+=".txt";
						fileName = absoluteOutputFolder + File.separator + nameBase;
						writer = new PrintWriter( fileName );
						calledTestCaseTestStepResult.writeTo( writer );
						// TODO
						// OutputFormatter.getInstance().appendTestStepResult((WsdlTestStepResult) calledTestCaseTestStepResult,
						//		calledTestCaseTestStep, nameBase);
						writer.close();
						runContext.setProperty("CalledRunTestCaseStep", null);
						runContext.setProperty("CalledTestCaseTestStepResult", null);
				}

				// write attachments
				if( result instanceof MessageExchange )
				{
					Attachment[] attachments = ( ( MessageExchange )result ).getResponseAttachments();
					if( attachments != null && attachments.length > 0 )
					{
						for( int c = 0; c < attachments.length; c++ )
						{
							fileName = nameBase + "-attachment-" + ( c + 1 ) + ".";

							Attachment attachment = attachments[c];
							String contentType = attachment.getContentType();
							if( !"application/octet-stream".equals( contentType ) && contentType != null
									&& contentType.indexOf( '/' ) != -1 )
							{
								fileName += contentType.substring( contentType.lastIndexOf( '/' ) + 1 );
							}
							else
							{
								fileName += "dat";
							}

							fileName = absoluteOutputFolder + File.separator + fileName;

							FileOutputStream outFile = new FileOutputStream( fileName );
							Tools.writeAll( outFile, attachment.getInputStream() );
							outFile.close();
						}
					}
				}

			exportCount++ ;
			}
			catch( Exception e )
			{
				log.error( "Error saving failed result: " + e, e );
			}
		}
		*/

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
