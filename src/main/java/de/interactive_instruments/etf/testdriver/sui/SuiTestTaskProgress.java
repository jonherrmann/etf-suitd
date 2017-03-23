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
package de.interactive_instruments.etf.testdriver.sui;

import com.eviware.soapui.model.environment.Environment;
import com.eviware.soapui.model.iface.Interface;
import com.eviware.soapui.model.mock.MockService;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.model.project.ProjectListener;
import com.eviware.soapui.model.testsuite.*;
import com.eviware.soapui.security.SecurityTest;

import de.interactive_instruments.etf.testdriver.AbstractTestTaskProgress;

/**
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
class SuiTestTaskProgress extends AbstractTestTaskProgress implements ProjectListener, TestSuiteListener {

	private int remainingSteps;

	@Override
	public void testStepAdded(final TestStep arg0, final int arg1) {
		this.remainingSteps++;
	}

	@Override
	public void testStepRemoved(final TestStep arg0, final int arg1) {
		this.remainingSteps--;
	}

	@Override
	public void interfaceAdded(final Interface anInterface) {

	}

	@Override
	public void interfaceRemoved(final Interface anInterface) {

	}

	@Override
	public void interfaceUpdated(final Interface anInterface) {

	}

	@Override
	public void testSuiteAdded(final TestSuite testSuite) {

	}

	@Override
	public void testSuiteRemoved(final TestSuite testSuite) {

	}

	@Override
	public void testSuiteMoved(final TestSuite testSuite, final int i, final int i1) {

	}

	@Override
	public void mockServiceAdded(final MockService mockService) {

	}

	@Override
	public void mockServiceRemoved(final MockService mockService) {

	}

	@Override
	public void afterLoad(final Project project) {
		int bla;
	}

	@Override
	public void beforeSave(final Project project) {

	}

	@Override
	public void environmentAdded(final Environment environment) {

	}

	@Override
	public void environmentRemoved(final Environment environment, final int i) {

	}

	@Override
	public void environmentSwitched(final Environment environment) {

	}

	@Override
	public void environmentRenamed(final Environment environment, final String s, final String s1) {

	}

	@Override
	public void testCaseAdded(final TestCase testCase) {

	}

	@Override
	public void testCaseRemoved(final TestCase testCase) {

	}

	@Override
	public void testCaseMoved(final TestCase testCase, final int i, final int i1) {

	}

	@Override
	public void loadTestAdded(final LoadTest loadTest) {

	}

	@Override
	public void loadTestRemoved(final LoadTest loadTest) {

	}

	@Override
	public void testStepMoved(final TestStep testStep, final int i, final int i1) {

	}

	@Override
	public void securityTestAdded(final SecurityTest securityTest) {

	}

	@Override
	public void securityTestRemoved(final SecurityTest securityTest) {

	}
}
