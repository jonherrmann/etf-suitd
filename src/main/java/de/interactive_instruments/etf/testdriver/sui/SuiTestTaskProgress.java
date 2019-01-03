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

import com.eviware.soapui.model.environment.Environment;
import com.eviware.soapui.model.iface.Interface;
import com.eviware.soapui.model.mock.MockService;
import com.eviware.soapui.model.project.Project;
import com.eviware.soapui.model.project.ProjectListener;
import com.eviware.soapui.model.testsuite.*;
import com.eviware.soapui.security.SecurityTest;

import de.interactive_instruments.etf.testdriver.AbstractTestTaskProgress;

/**
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
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
