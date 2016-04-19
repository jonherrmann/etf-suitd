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

import java.io.IOException;

import de.interactive_instruments.etf.dal.dto.plan.TestRunDto;
import de.interactive_instruments.etf.driver.InvalidTestParameterException;
import de.interactive_instruments.etf.model.plan.AbstractTestRun;
import de.interactive_instruments.etf.model.plan.TestObject;
import de.interactive_instruments.etf.model.plan.TestProject;
import de.interactive_instruments.etf.model.plan.TestRun;
import de.interactive_instruments.etf.model.result.TestReport;
import de.interactive_instruments.exceptions.InitializationException;
import de.interactive_instruments.properties.PropertyHolder;

/**
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
public class SuiTestRun extends AbstractTestRun {

	protected SuiTestRun(TestRunDto testRunDto, TestObject testObject, TestReport report, TestProject testProject) {
		super(testRunDto.getId(), "SUI", testObject, report, testProject, "SoapUI", testRunDto.getProperties());
	}

	@Override
	public String getUsernameOfInitiator() {
		return null;
	}

	@Override
	public String getGenerator() {
		return null;
	}

}
