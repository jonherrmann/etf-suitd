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

import java.io.*;
import java.net.MalformedURLException;
import java.security.GeneralSecurityException;
import java.util.TimerTask;

import javax.swing.*;

import com.eviware.soapui.DefaultSoapUICore;
import com.eviware.soapui.SoapUI;
import com.eviware.soapui.SoapUICore;
import com.eviware.soapui.SoapUIExtensionClassLoader;
import com.eviware.soapui.config.SoapuiSettingsDocumentConfig;
import com.eviware.soapui.impl.settings.XmlBeansSettingsImpl;
import com.eviware.soapui.impl.wsdl.support.http.HttpClientSupport;
import com.eviware.soapui.impl.wsdl.support.soap.SoapVersion;
import com.eviware.soapui.model.propertyexpansion.PropertyExpansionUtils;
import com.eviware.soapui.model.settings.Settings;
import com.eviware.soapui.monitor.MockEngine;
import com.eviware.soapui.security.registry.SecurityScanRegistry;
import com.eviware.soapui.settings.*;
import com.eviware.soapui.support.SecurityScanUtil;
import com.eviware.soapui.support.StringUtils;
import com.eviware.soapui.support.action.SoapUIActionRegistry;
import com.eviware.soapui.support.factory.SoapUIFactoryRegistry;
import com.eviware.soapui.support.listener.SoapUIListenerRegistry;
import com.eviware.soapui.support.types.StringList;

import org.apache.commons.ssl.OpenSSL;
import org.slf4j.Logger;

/**
 * Initializes core objects. Adapter for the SoapUICore based on the
 * command line runner SoapUICore.
 *
 * @author ole.matzura
 *
 * @author Jon Herrmann ( herrmann aT interactive-instruments doT de )
 *
 */
class IISoapUICore implements SoapUICore {
	public static Logger log;

	private boolean logIsInitialized;
	private String root;
	protected SoapuiSettingsDocumentConfig settingsDocument;
	private volatile MockEngine mockEngine;
	private XmlBeansSettingsImpl settings;
	private SoapUIListenerRegistry listenerRegistry;
	private SoapUIActionRegistry actionRegistry;
	private SoapUIFactoryRegistry factoryRegistry;
	private long lastSettingsLoad = 0;

	private String settingsFile;
	private String password;
	protected boolean initialImport;
	private TimerTask settingsWatcher;
	private SoapUIExtensionClassLoader extClassLoader;

	public boolean isSavingSettings;

	public boolean getInitialImport() {
		return initialImport;
	}

	public void setInitialImport(boolean initialImport) {
		this.initialImport = initialImport;
	}

	public static IISoapUICore createDefault() {
		return new IISoapUICore(null, DEFAULT_SETTINGS_FILE);
	}

	public IISoapUICore() {}

	/*
	 * this method is added for enabling settings password (like in core) all the
	 * way down in hierarchy boolean setingPassword is a dummy parameter, because
	 * the constructor with only one string parameter already existed
	 */
	public IISoapUICore(boolean settingPassword, String soapUISettingsPassword) {
		this.password = soapUISettingsPassword;
	}

	public IISoapUICore(String root) {
		this.root = root;
	}

	public IISoapUICore(String root, String settingsFile) {
		this(root);
		init(settingsFile);
	}

	public IISoapUICore(String root, String settingsFile, String password) {
		this(root);
		this.password = password;
		init(settingsFile);
	}

	public void init(String settingsFile) {
		initLog();

		SoapUI.setSoapUICore(this);

		loadExternalLibraries();
		initSettings(settingsFile == null ? DEFAULT_SETTINGS_FILE : settingsFile);
		initPlugins();

		// this is to provoke initialization
		SoapVersion.Soap11.equals(SoapVersion.Soap12);
	}

	protected void initPlugins() {
		final ClassLoader componentClassLoader = this.getClass().getClassLoader();
		final InputStream factoriesStream = componentClassLoader.getResourceAsStream("META-INF/factories.xml");
		if (factoriesStream == null) {
			throw new IllegalStateException("Could not load plugin factory "
					+ " configuration file from packaged SoapUI test driver");
		}
		getFactoryRegistry().addConfig(factoriesStream, componentClassLoader);
	}

	public String getRoot() {
		if (root == null || root.length() == 0)
			root = System.getProperty("soapui.home", new File(".").getAbsolutePath());
		return root;
	}

	protected Settings initSettings(String fileName) {
		// TODO Why try to load settings from current directory before using root?
		// This caused a bug in Eclipse:
		// https://sourceforge.net/tracker/?func=detail&atid=737763&aid=2620284&group_id=136013
		File settingsFile = new File(fileName).exists() ? new File(fileName) : null;

		try {
			if (settingsFile == null) {
				settingsFile = new File(new File(getRoot()), DEFAULT_SETTINGS_FILE);
				if (!settingsFile.exists()) {
					settingsFile = new File(new File(System.getProperty("user.home", ".")), DEFAULT_SETTINGS_FILE);
					lastSettingsLoad = 0;
				}
			} else {
				settingsFile = new File(fileName);
				if (!settingsFile.getAbsolutePath().equals(this.settingsFile)) {
					lastSettingsLoad = 0;
				}
			}

			if (!settingsFile.exists()) {
				if (settingsDocument == null) {
					log.info("Creating new settings at [" + settingsFile.getAbsolutePath() + "]");
					settingsDocument = SoapuiSettingsDocumentConfig.Factory.newInstance();
					setInitialImport(true);
				}

				lastSettingsLoad = System.currentTimeMillis();
			} else if (settingsFile.lastModified() > lastSettingsLoad) {
				settingsDocument = SoapuiSettingsDocumentConfig.Factory.parse(settingsFile);

				byte[] encryptedContent = settingsDocument.getSoapuiSettings().getEncryptedContent();
				if (encryptedContent != null) {
					char[] password = null;
					if (this.password == null) {
						// swing element -!! uh!
						JPasswordField passwordField = new JPasswordField();
						JLabel qLabel = new JLabel("Password");
						JOptionPane.showConfirmDialog(null, new Object[]{qLabel, passwordField}, "Global Settings",
								JOptionPane.OK_CANCEL_OPTION);
						password = passwordField.getPassword();
					} else {
						password = this.password.toCharArray();
					}

					String encryptionAlgorithm = settingsDocument.getSoapuiSettings().getEncryptedContentAlgorithm();
					byte[] data = OpenSSL.decrypt(StringUtils.isNullOrEmpty(encryptionAlgorithm) ? "des3" : encryptionAlgorithm,
							password, encryptedContent);
					try {
						settingsDocument = SoapuiSettingsDocumentConfig.Factory.parse(new String(data, "UTF-8"));
					} catch (Exception e) {
						log.warn("Wrong password.");
						JOptionPane.showMessageDialog(null, "Wrong password, creating backup settings file [ "
								+ settingsFile.getAbsolutePath() + ".bak.xml. ]\nSwitch to default settings.",
								"Error - Wrong Password", JOptionPane.ERROR_MESSAGE);
						settingsDocument.save(new File(settingsFile.getAbsolutePath() + ".bak.xml"));
						throw e;
					}
				}

				log.info("initialized soapui-settings from [" + settingsFile.getAbsolutePath() + "]");
				lastSettingsLoad = settingsFile.lastModified();
			}
		} catch (Exception e) {
			log.warn("Failed to load settings from [" + e.getMessage() + "], creating new");
			settingsDocument = SoapuiSettingsDocumentConfig.Factory.newInstance();
			lastSettingsLoad = 0;
		}

		if (settingsDocument.getSoapuiSettings() == null) {
			settingsDocument.addNewSoapuiSettings();
			settings = new XmlBeansSettingsImpl(null, null, settingsDocument.getSoapuiSettings());

			initDefaultSettings(settings);
		} else {
			settings = new XmlBeansSettingsImpl(null, null, settingsDocument.getSoapuiSettings());
		}

		this.settingsFile = settingsFile.getAbsolutePath();

		if (!settings.isSet(WsdlSettings.EXCLUDED_TYPES)) {
			StringList list = new StringList();
			list.add("schema@http://www.w3.org/2001/XMLSchema");
			settings.setString(WsdlSettings.EXCLUDED_TYPES, list.toXml());
		}

		if (settings.getString(HttpSettings.HTTP_VERSION, HttpSettings.HTTP_VERSION_1_1).equals(
				HttpSettings.HTTP_VERSION_0_9)) {
			settings.setString(HttpSettings.HTTP_VERSION, HttpSettings.HTTP_VERSION_1_1);
		}

		setIfNotSet(WsdlSettings.NAME_WITH_BINDING, true);
		setIfNotSet(WsdlSettings.NAME_WITH_BINDING, 500);
		setIfNotSet(HttpSettings.HTTP_VERSION, HttpSettings.HTTP_VERSION_1_1);
		setIfNotSet(HttpSettings.MAX_TOTAL_CONNECTIONS, 2000);
		setIfNotSet(HttpSettings.RESPONSE_COMPRESSION, true);
		setIfNotSet(HttpSettings.LEAVE_MOCKENGINE, true);
		setIfNotSet(UISettings.AUTO_SAVE_PROJECTS_ON_EXIT, true);
		setIfNotSet(UISettings.SHOW_DESCRIPTIONS, true);
		setIfNotSet(WsdlSettings.XML_GENERATION_ALWAYS_INCLUDE_OPTIONAL_ELEMENTS, true);
		setIfNotSet(WsaSettings.USE_DEFAULT_RELATES_TO, true);
		setIfNotSet(WsaSettings.USE_DEFAULT_RELATIONSHIP_TYPE, true);
		setIfNotSet(UISettings.SHOW_STARTUP_PAGE, true);
		setIfNotSet(UISettings.GC_INTERVAL, "60");
		setIfNotSet(WsdlSettings.CACHE_WSDLS, true);
		setIfNotSet(WsdlSettings.PRETTY_PRINT_RESPONSE_MESSAGES, true);
		setIfNotSet(HttpSettings.RESPONSE_COMPRESSION, true);
		setIfNotSet(HttpSettings.INCLUDE_REQUEST_IN_TIME_TAKEN, true);
		setIfNotSet(HttpSettings.INCLUDE_RESPONSE_IN_TIME_TAKEN, true);
		setIfNotSet(HttpSettings.LEAVE_MOCKENGINE, true);
		setIfNotSet(HttpSettings.START_MOCK_SERVICE, true);
		setIfNotSet(UISettings.AUTO_SAVE_INTERVAL, "0");
		setIfNotSet(UISettings.GC_INTERVAL, "60");
		setIfNotSet(UISettings.SHOW_STARTUP_PAGE, true);
		setIfNotSet(WsaSettings.SOAP_ACTION_OVERRIDES_WSA_ACTION, false);
		setIfNotSet(WsaSettings.USE_DEFAULT_RELATIONSHIP_TYPE, true);
		setIfNotSet(WsaSettings.USE_DEFAULT_RELATES_TO, true);
		setIfNotSet(WsaSettings.OVERRIDE_EXISTING_HEADERS, false);
		setIfNotSet(WsaSettings.ENABLE_FOR_OPTIONAL, false);
		setIfNotSet(VersionUpdateSettings.AUTO_CHECK_VERSION_UPDATE, true);
		if (!settings.isSet(ProxySettings.AUTO_PROXY) && !settings.isSet(ProxySettings.ENABLE_PROXY)) {
			settings.setBoolean(ProxySettings.AUTO_PROXY, true);
			settings.setBoolean(ProxySettings.ENABLE_PROXY, true);
		}

		boolean setWsiDir = false;
		String wsiLocationString = settings.getString(WSISettings.WSI_LOCATION, null);
		if (StringUtils.isNullOrEmpty(wsiLocationString)) {
			setWsiDir = true;
		} else {
			File wsiFile = new File(wsiLocationString);
			if (!wsiFile.exists()) {
				setWsiDir = true;
			}
		}
		if (setWsiDir) {
			String wsiDir = System.getProperty("wsi.dir", new File(".").getAbsolutePath());
			settings.setString(WSISettings.WSI_LOCATION, wsiDir);
		}
		HttpClientSupport.addSSLListener(settings);

		return settings;
	}

	private void setIfNotSet(String id, boolean value) {
		if (!settings.isSet(id))
			settings.setBoolean(id, true);
	}

	private void setIfNotSet(String id, String value) {
		if (!settings.isSet(id))
			settings.setString(id, value);
	}

	private void setIfNotSet(String id, long value) {
		if (!settings.isSet(id))
			settings.setLong(id, value);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.eviware.soapui.SoapUICore#importSettings(java.io.File)
	 */
	public Settings importSettings(File file) throws Exception {
		if (file != null) {
			log.info("Importing preferences from [" + file.getAbsolutePath() + "]");
			return initSettings(file.getAbsolutePath());
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.eviware.soapui.SoapUICore#getSettings()
	 */
	public Settings getSettings() {
		if (settings == null) {
			initSettings(DEFAULT_SETTINGS_FILE);
		}

		return settings;
	}

	protected void initDefaultSettings(Settings settings2) {

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.eviware.soapui.SoapUICore#saveSettings()
	 */
	public String saveSettings() throws Exception {
		PropertyExpansionUtils.saveGlobalProperties();
		SecurityScanUtil.saveGlobalSecuritySettings();
		isSavingSettings = true;
		try {
			if (settingsFile == null)
				settingsFile = getRoot() + File.separatorChar + DEFAULT_SETTINGS_FILE;

			// Save settings to root or user.home
			File file = new File(settingsFile);
			if (!file.canWrite()) {
				file = new File(new File(System.getProperty("user.home", ".")), DEFAULT_SETTINGS_FILE);
			}

			SoapuiSettingsDocumentConfig settingsDocument = (SoapuiSettingsDocumentConfig) this.settingsDocument.copy();
			String password = settings.getString(SecuritySettings.SHADOW_PASSWORD, null);

			if (password != null && password.length() > 0) {
				try {
					byte[] data = settingsDocument.xmlText().getBytes();
					String encryptionAlgorithm = "des3";
					byte[] encryptedData = OpenSSL.encrypt(encryptionAlgorithm, password.toCharArray(), data);
					settingsDocument.setSoapuiSettings(null);
					settingsDocument.getSoapuiSettings().setEncryptedContent(encryptedData);
					settingsDocument.getSoapuiSettings().setEncryptedContentAlgorithm(encryptionAlgorithm);
				} catch (UnsupportedEncodingException e) {
					log.error("Encryption error", e);
				} catch (IOException e) {
					log.error("Encryption error", e);
				} catch (GeneralSecurityException e) {
					log.error("Encryption error", e);
				}
			}

			FileOutputStream out = new FileOutputStream(file);
			settingsDocument.save(out);
			out.flush();
			out.close();
			log.info("Settings saved to [" + file.getAbsolutePath() + "]");
			lastSettingsLoad = file.lastModified();
			return file.getAbsolutePath();
		} finally {
			isSavingSettings = false;
		}
	}

	public String getSettingsFile() {
		return settingsFile;
	}

	public void setSettingsFile(String settingsFile) {
		this.settingsFile = settingsFile;
	}

	protected void initLog() {
		if (!logIsInitialized) {
			logIsInitialized = true;

			log = org.slf4j.LoggerFactory.getLogger(IISoapUICore.class);
		}
	}

	public synchronized void loadExternalLibraries() {
		if (extClassLoader == null) {
			try {
				extClassLoader = SoapUIExtensionClassLoader.create(getRoot(), getExtensionClassLoaderParent());
			} catch (MalformedURLException e) {
				SoapUI.logError(e);
			}
		}
	}

	protected ClassLoader getExtensionClassLoaderParent() {
		return SoapUI.class.getClassLoader();
	}

	public SoapUIExtensionClassLoader getExtensionClassLoader() {
		if (extClassLoader == null)
			loadExternalLibraries();

		return extClassLoader;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.eviware.soapui.SoapUICore#getMockEngine()
	 */
	public MockEngine getMockEngine() {
		if (mockEngine == null) {
			synchronized (IISoapUICore.class) {
				if (mockEngine == null) {
					mockEngine = buildMockEngine();
				}
			}
		}

		return mockEngine;
	}

	protected MockEngine buildMockEngine() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.eviware.soapui.SoapUICore#getListenerRegistry()
	 */
	public SoapUIListenerRegistry getListenerRegistry() {
		if (listenerRegistry == null)
			initListenerRegistry();

		return listenerRegistry;
	}

	protected void initListenerRegistry() {
		listenerRegistry = new SoapUIListenerRegistry(null);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.eviware.soapui.SoapUICore#getActionRegistry()
	 */
	public SoapUIActionRegistry getActionRegistry() {
		if (actionRegistry == null)
			actionRegistry = initActionRegistry();

		return actionRegistry;
	}

	protected SoapUIActionRegistry initActionRegistry() {
		return new SoapUIActionRegistry(
				DefaultSoapUICore.class.getResourceAsStream("/com/eviware/soapui/resources/conf/soapui-actions.xml"));
	}

	protected void initFactoryRegistry() {
		factoryRegistry = new SoapUIFactoryRegistry(null);
	}

	@Override
	public SoapUIFactoryRegistry getFactoryRegistry() {
		if (factoryRegistry == null)
			initFactoryRegistry();

		return factoryRegistry;
	}

	@Override
	public SecurityScanRegistry getSecurityScanRegistry() {
		return SecurityScanRegistry.getInstance();
	}
}
