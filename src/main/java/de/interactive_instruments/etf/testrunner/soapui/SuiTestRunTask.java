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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.eviware.soapui.STestCaseRunner;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.model.testsuite.TestProperty;
import com.eviware.soapui.settings.HttpSettings;
import com.eviware.soapui.tools.AbstractSoapUITestRunner;

import de.interactive_instruments.IFile;
import de.interactive_instruments.concurrent.InvalidStateTransitionException;
import de.interactive_instruments.concurrent.TaskProgress;
import de.interactive_instruments.etf.dal.IReportDep;
import de.interactive_instruments.etf.driver.AbstractTestRunTask;
import de.interactive_instruments.etf.model.plan.TestProject;
import de.interactive_instruments.etf.model.result.TestReport;
import de.interactive_instruments.etf.sel.model.impl.result.SuiTestReport;
import de.interactive_instruments.etf.sel.model.mapping.SuiTestProject;

/**
 * SoapUI TestRunTask which starts the SoapUI test runner and injects
 * the result collector
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
public class SuiTestRunTask extends AbstractTestRunTask {

	private final TestProject proj;
	private final SuiTestReport report;
	private WsdlProject wsdlProject = null;
	private STestCaseRunner runner = null;

	public SuiTestRunTask(SuiTestRun testRun) throws IOException {
		super(new SuiTestRunnerProgress(), testRun);
		// Change when log4j is removed
		final IFile dsDir = new IFile(new File(
				this.testRun.getReport().getPublicationLocation()).getParentFile().getParentFile());
		final IFile appenixDir = dsDir.expandPath("appendices/" +
				this.testRun.getReport().getId().toString());
		final String logFileName = appenixDir.secureExpandPathDown(
				this.testRun.getReport().getId().toString() + ".log").getPath();
		setWlogAppender(logFileName);
		this.proj = testRun.getTestProject();
		this.report = (SuiTestReport) testRun.getReport();
		logInfo("SuiTestRunTask created");
	}

	@Override
	protected void doHandleException(Exception e) {

	}

	@Override
	protected TestReport doStartTestRunnner() throws Exception {
		Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());

		final IFile projectFile = ((SuiTestProject) proj).getTmpProjectFileCopy();
		// Set Soapui project Properties
		List<String> properties = new ArrayList<String>();

		for (Map.Entry<String, String> kvp : testRun.namePropertyPairs()) {
			if (kvp.getKey() != null) {
				properties.add(kvp.getKey());
				properties.add(kvp.getValue());
			}
		}
		report.setTestRunProperties(testRun);

		// Set test object resources
		testRun.getTestObject().getResources().entrySet().forEach(r -> {
			properties.add(r.getKey().toString());
			properties.add(r.getValue().toString());
		});

		properties.add("username");
		properties.add(testRun.getTestObject().getPropertyOrDefault("username", ""));
		properties.add("password");
		properties.add(testRun.getTestObject().getPropertyOrDefault("password", ""));
		properties.add("authUser");
		properties.add(testRun.getTestObject().getPropertyOrDefault("username", ""));
		properties.add("authPwd");
		properties.add(testRun.getTestObject().getPropertyOrDefault("password", ""));
		properties.add("authMethod");
		properties.add("basic");

		// Run Functional Tests ( also for generating Request for LoadTests )
		runner = new STestCaseRunner((SuiTestRunnerProgress) taskProgress);
		runner.setProjectProperties(properties.toArray(new String[properties.size()]));
		// Deactivate UI funtions
		runner.setEnableUI(false);

		// Set project file
		runner.setProjectFile(projectFile.getAbsolutePath());

		runner.setOutputFolder(testRun.getReport().getAppendixLocation().getPath());

		wsdlProject = runner.initProject();

		if (wsdlProject.getActiveEnvironment() instanceof IReportDep) {
			// Inject result collector
			((IReportDep) wsdlProject.getActiveEnvironment()).injectReport(null, this.testRun.getReport());
		}

		fireInitialized();

		logInfo("Project Properties: ");
		final String[] outProps = runner.getProjectProperties();
		for (int i = 0; i < runner.getProjectProperties().length; i += 2) {
			final String key = outProps[i];
			final String val = outProps[i + 1];
			if (!"authPwd".equals(key) && !"password".equals(key)) {
				logInfo(key + " - " + val);
			}
		}

		fireRunning();
		runner.runRunner();
		return testRun.getReport();
	}

	@Override
	protected void doRelease() {
		if (wsdlProject != null) {
			if (wsdlProject.getActiveEnvironment() != null) {
				wsdlProject.getActiveEnvironment().release();
			}
			wsdlProject.release();
		}
	}

	@Override
	protected void doCancel() throws InvalidStateTransitionException {
		if (runner != null) {
			runner.cancel();
		}
	}
}
