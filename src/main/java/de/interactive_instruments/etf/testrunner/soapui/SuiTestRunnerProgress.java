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

import com.eviware.soapui.model.environment.Environment;
import com.eviware.soapui.model.iface.Interface;
import com.eviware.soapui.model.mock.MockService;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.model.project.ProjectListener;
import com.eviware.soapui.model.testsuite.LoadTest;
import com.eviware.soapui.model.testsuite.TestCase;
import com.eviware.soapui.model.testsuite.TestCaseRunContext;
import com.eviware.soapui.model.testsuite.TestCaseRunner;
import com.eviware.soapui.model.testsuite.TestRunListener;
import com.eviware.soapui.model.testsuite.TestStep;
import com.eviware.soapui.model.testsuite.TestStepResult;
import com.eviware.soapui.model.testsuite.TestSuite;
import com.eviware.soapui.model.testsuite.TestSuiteListener;
import com.eviware.soapui.security.SecurityTest;

import org.apache.log4j.Appender;

import de.interactive_instruments.concurrent.AbstractTaskProgress;
import de.interactive_instruments.etf.model.result.TestReport;

/**
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
public final class SuiTestRunnerProgress extends AbstractTaskProgress<TestReport>implements TestSuiteListener, ProjectListener, TestRunListener {

	SuiTestRunnerProgress() {
		remainingSteps = 1000;
		stepsCompleted = 1;
	}

	public void setMaxSteps(int max) {
		this.remainingSteps = max;
	}

	public void advanceStep() {
		stepsCompleted++;

		// Just increase the max steps (in SoapUI the number of generated
		// tests steps is unknown )
		if (stepsCompleted >= remainingSteps) {
			remainingSteps++;
		}
	}

	public Appender getAppender() {
		return appender;
	}

	// Listeners

	@Override
	public void testStepAdded(final TestStep arg0, final int arg1) {
		this.remainingSteps++;
	}

	@Override
	public void testStepRemoved(final TestStep arg0, final int arg1) {
		this.remainingSteps--;
	}

	@Override
	public void testSuiteAdded(final TestSuite testSuite) {
		testSuite.addTestSuiteListener(this);
	}

	@Override
	public void afterStep(final TestCaseRunner arg0, final TestCaseRunContext arg1,
			final TestStepResult arg2) {
		advanceStep();
	}

	///////////////////
	// Dummies
	///////////////////

	@Override
	public void loadTestAdded(final LoadTest arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void loadTestRemoved(final LoadTest arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void securityTestAdded(final SecurityTest arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void securityTestRemoved(final SecurityTest arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void testCaseAdded(final TestCase arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void testCaseMoved(final TestCase arg0, final int arg1, final int arg2) {
		// TODO Auto-generated method stub

	}

	@Override
	public void testCaseRemoved(final TestCase arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void testStepMoved(final TestStep arg0, final int arg1, final int arg2) {
		// TODO Auto-generated method stub

	}

	@Override
	public void afterLoad(final Project arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void beforeSave(final Project arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void environmentAdded(final Environment arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void environmentRemoved(final Environment arg0, final int arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void environmentRenamed(final Environment arg0, final String arg1, final String arg2) {
		// TODO Auto-generated method stub

	}

	@Override
	public void environmentSwitched(final Environment arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void interfaceAdded(final Interface arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void interfaceRemoved(final Interface arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void interfaceUpdated(final Interface arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mockServiceAdded(final MockService arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void mockServiceRemoved(final MockService arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void testSuiteMoved(final TestSuite arg0, final int arg1, final int arg2) {
		// TODO Auto-generated method stub

	}

	@Override
	public void testSuiteRemoved(final TestSuite arg0) {
		// TODO Auto-generated method stub
	}

	@Override
	public void afterRun(TestCaseRunner arg0, TestCaseRunContext arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void beforeRun(TestCaseRunner arg0, TestCaseRunContext arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void beforeStep(TestCaseRunner arg0, TestCaseRunContext arg1) {
		// TODO Auto-generated method stub

	}

	@Override
	public void beforeStep(TestCaseRunner arg0, TestCaseRunContext arg1,
			TestStep arg2) {
		// TODO Auto-generated method stub

	}
}
