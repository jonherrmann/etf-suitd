/**
 * Copyright 2010-2017 interactive instruments GmbH
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.eviware.soapui.impl.wsdl.WsdlProject;

import de.interactive_instruments.IFile;
import de.interactive_instruments.etf.dal.dto.run.TestTaskDto;
import de.interactive_instruments.etf.testdriver.AbstractTestTask;
import de.interactive_instruments.etf.testdriver.ExecutableTestSuiteUnavailable;
import de.interactive_instruments.etf.testdriver.TestResultCollectorInjector;
import de.interactive_instruments.exceptions.InitializationException;
import de.interactive_instruments.exceptions.InvalidStateTransitionException;
import de.interactive_instruments.exceptions.config.ConfigurationException;

/**
 * BaseX test run task for executing XQuery on a BaseX database.
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 */
class SuiTestTask extends AbstractTestTask {

	private WsdlProject wsdlProject = null;
	private STestCaseRunner runner = null;
	private IFile tmpProjectFile;

	/**
	 * Default constructor.
	 *
	 * @throws IOException I/O error
	 */
	public SuiTestTask(final TestTaskDto testTaskDto) {
		super(testTaskDto, new SuiTestTaskProgress(), SuiTestTask.class.getClassLoader());
	}

	@Override
	protected void doRun() throws Exception {
		runner.runRunner();
	}

	@Override
	protected void doInit() throws ConfigurationException, InitializationException {
		try {
			if (testTaskDto.getExecutableTestSuite().getLocalPath() == null) {
				throw new InitializationException("Required property 'localPath' must be set!");
			}
			final IFile originalProjectFile = new IFile(testTaskDto.getExecutableTestSuite().getLocalPath());
			originalProjectFile.expectFileIsReadable();
			tmpProjectFile = originalProjectFile.createTempCopy(IFile.sanitize(
					testTaskDto.getExecutableTestSuite().getLabel()) + "_ets", "etf");
			tmpProjectFile.expectIsReadAndWritable();

			// Set Soapui project Properties
			final List<String> properties = new ArrayList<String>();

			testTaskDto.getArguments().values().entrySet().stream().filter(kvp -> kvp.getKey() != null).forEach(kvp -> {
				properties.add(kvp.getKey());
				properties.add(kvp.getValue());
			});

			// Set test object resources
			testTaskDto.getTestObject().getResources().entrySet().forEach(r -> {
				properties.add(r.getKey());
				properties.add(r.getValue().getUri().toString());
			});

			properties.add("username");
			properties.add(testTaskDto.getTestObject().properties().getPropertyOrDefault("username", ""));
			properties.add("password");
			properties.add(testTaskDto.getTestObject().properties().getPropertyOrDefault("password", ""));
			properties.add("authUser");
			properties.add(testTaskDto.getTestObject().properties().getPropertyOrDefault("username", ""));
			properties.add("authPwd");
			properties.add(testTaskDto.getTestObject().properties().getPropertyOrDefault("password", ""));
			properties.add("authMethod");
			properties.add("basic");

			// Run Functional Tests ( also for generating Request for LoadTests )
			runner = new STestCaseRunner((SuiTestTaskProgress) progress);
			runner.setProjectProperties(properties.toArray(new String[properties.size()]));

			// Deactivate UI funtions
			runner.setEnableUI(false);

			// Set project file
			runner.setProjectFile(tmpProjectFile.getAbsolutePath());

			runner.setOutputFolder(getCollector().getTempDir().getAbsolutePath());

			wsdlProject = runner.initProject(getCollector());
			if (wsdlProject.getActiveEnvironment() instanceof TestResultCollectorInjector) {
				((TestResultCollectorInjector) wsdlProject.getActiveEnvironment())
						.setTestResultCollector(getPersistor().getResultCollector());
			}

			getLogger().info("Project Properties: ");
			final String[] outProps = runner.getProjectProperties();
			for (int i = 0; i < runner.getProjectProperties().length; i += 2) {
				final String key = outProps[i];
				final String val = outProps[i + 1];
				if (!"authPwd".equals(key) && !"password".equals(key)) {
					getLogger().info("{} - {} ", key, val);
				}
			}

		} catch (Exception e) {
			throw new ExecutableTestSuiteUnavailable(testTaskDto.getExecutableTestSuite(), e);
		}
	}

	@Override
	public void doRelease() {
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
