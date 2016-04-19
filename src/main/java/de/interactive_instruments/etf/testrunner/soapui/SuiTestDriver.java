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

import com.eviware.soapui.SoapUI;

import de.interactive_instruments.etf.component.ComponentInfo;
import de.interactive_instruments.etf.component.ComponentInitializer;
import de.interactive_instruments.etf.driver.TestDriver;
import de.interactive_instruments.etf.driver.TestRunTaskFactory;

/**
 * Test Driver metadata information
 *
 * @author J. Herrmann ( herrmann <aT) interactive-instruments (doT> de )
 */
@ComponentInitializer(id = "SUI")
public class SuiTestDriver implements TestDriver {

	final TestRunTaskFactory factory = new SuiTestRunTaskFactory();
	final ComponentInfo info = new ComponentInfo() {

		@Override
		public String getName() {
			return "SoapUI test driver";
		}

		@Override
		public String getId() {
			return "SUI";
		}

		@Override
		public String getVersion() {
			return this.getClass().getPackage().getImplementationVersion();
		}

		@Override
		public String getVendor() {
			return this.getClass().getPackage().getImplementationVendor();
		}

		@Override
		public String getDescription() {
			return "Test driver for SoapUI " + SoapUI.class.getPackage().getImplementationVersion();
		}
	};

	public SuiTestDriver() {}

	@Override
	public ComponentInfo getTestDriverInfo() {
		return info;
	}

	@Override
	public TestRunTaskFactory getTestRunTaskFactory() {
		return factory;
	}

	@Override
	public File getTestDriverLog() {
		return null;
	}

	@Override
	public void release() {
		factory.release();
	}
}
