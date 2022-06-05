/*
 * Copyright OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.facesviews;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;
import static java.util.Locale.ENGLISH;
import static java.util.Locale.US;
import static java.util.regex.Pattern.quote;
import static javax.faces.view.facelets.ResourceResolver.FACELETS_RESOURCE_RESOLVER_PARAM_NAME;
import static javax.servlet.DispatcherType.FORWARD;
import static javax.servlet.DispatcherType.REQUEST;
import static org.omnifaces.facesviews.ExtensionAction.REDIRECT_TO_EXTENSIONLESS;
import static org.omnifaces.facesviews.FacesServletDispatchMethod.DO_FILTER;
import static org.omnifaces.facesviews.PathAction.SEND_404;
import static org.omnifaces.facesviews.ViewHandlerMode.STRIP_EXTENSION_FROM_PARENT;
import static org.omnifaces.util.Faces.getApplicationFromFactory;
import static org.omnifaces.util.Faces.getServletContext;
import static org.omnifaces.util.Platform.getFacesServletMappings;
import static org.omnifaces.util.Platform.getFacesServletRegistration;
import static org.omnifaces.util.ResourcePaths.filterExtension;
import static org.omnifaces.util.ResourcePaths.getExtension;
import static org.omnifaces.util.ResourcePaths.isDirectory;
import static org.omnifaces.util.ResourcePaths.isExtensionless;
import static org.omnifaces.util.ResourcePaths.stripExtension;
import static org.omnifaces.util.ResourcePaths.stripPrefixPath;
import static org.omnifaces.util.ResourcePaths.stripTrailingSlash;
import static org.omnifaces.util.Servlets.getApplicationAttribute;
import static org.omnifaces.util.Servlets.getRequestBaseURL;
import static org.omnifaces.util.Utils.csvToList;
import static org.omnifaces.util.Utils.isEmpty;
import static org.omnifaces.util.Utils.startsWithOneOf;
import static org.omnifaces.util.Xml.getNodeTextContents;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Collator;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.faces.application.Application;
import javax.faces.application.ViewHandler;
import javax.faces.context.FacesContext;
import javax.faces.webapp.FacesServlet;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;

import org.omnifaces.ApplicationInitializer;
import org.omnifaces.ApplicationListener;
import org.omnifaces.cdi.Param;
import org.omnifaces.util.ResourcePaths;

/**
 * <p>
 * FacesViews is a mechanism to use SEO-friendly extensionless URLs in a JSF application without the need to enlist
 * individual Facelet source files in some configuration file.
 * <p>
 * By default, all URLs generated by {@link ViewHandler#getActionURL(FacesContext, String)}, which is used by among
 * others <code>&lt;h:form&gt;</code>, <code>&lt;h:link&gt;</code>, <code>&lt;h:button&gt;</code> and all extended tags,
 * will also be extensionless. And, URLs with an extension will be 301-redirected to the extensionless one.
 *
 * <h3>Usage</h3>
 *
 * <h4>Zero configuration</h4>
 * <p>
 * Put Facelets source files into <code>/WEB-INF/faces-views</code> directory. All Facelets files in this special
 * directory will be automatically scanned as extensionless URLs.
 *
 * <h4>Minimal configuration</h4>
 * <p>
 * Below is the minimal <code>web.xml</code> configuration to make all Facelets source files found in the root folder
 * and all subdirectories of the public web content (excluding <code>/WEB-INF</code>, <code>/META-INF</code> and
 * <code>/resources</code>) available as extensionless URLs:
 * <pre>
 * &lt;context-param&gt;
 *     &lt;param-name&gt;org.omnifaces.FACES_VIEWS_SCAN_PATHS&lt;/param-name&gt;
 *     &lt;param-value&gt;/*.xhtml&lt;/param-value&gt;
 * &lt;/context-param&gt;
 * </pre>
 * <p>
 * The path pattern <code>/*.xhtml</code> basically means that all files with the <code>.xhtml</code> extension from the
 * directory <code>/</code> must be scanned, including all sub directories. In case you want to scan only
 * <code>.xhtml</code> files in the directory <code>/foo</code>, then use path pattern of <code>/foo/*.xhtml</code>
 * instead. In case you want to scan <em>all</em> files in the directory <code>/foo</code>, then use path pattern of
 * <code>/foo</code>. You can specify multiple values separated by a comma.
 *
 * <h4>MultiViews configuration</h4>
 * <p>
 * Enabling MultiViews is a matter of suffixing the path pattern with <code>/*</code>. The support was added in
 * OmniFaces 2.5. Below is the <code>web.xml</code> configuration which extends the above minimal configuration with
 * MultiViews support:
 * <pre>
 * &lt;context-param&gt;
 *     &lt;param-name&gt;org.omnifaces.FACES_VIEWS_SCAN_PATHS&lt;/param-name&gt;
 *     &lt;param-value&gt;/*.xhtml/*&lt;/param-value&gt;
 * &lt;/context-param&gt;
 * </pre>
 * <p>
 * On an example URL of <code>http://example.com/context/foo/bar/baz</code> when neither <code>/foo/bar/baz.xhtml</code>
 * nor <code>/foo/bar.xhtml</code> exist, but <code>/foo.xhtml</code> does exist, then the request will forward to
 * <code>/foo.xhtml</code> and make the values <code>bar</code> and <code>baz</code> available as injectable path
 * parameters via <code>&#64;</code>{@link Param} in the managed bean associated with <code>/foo.xhtml</code>.
 * <pre>
 * &#64;Inject &#64;Param(pathIndex=0)
 * private String bar;
 *
 * &#64;Inject &#64;Param(pathIndex=1)
 * private String baz;
 * </pre>
 *
 * <h4>Advanced configuration</h4>
 * <p>
 * See <a href="package-summary.html">package documentation</a> for configuration settings as to mapping, filtering
 * and forwarding behavior.
 *
 * <h3>PrettyFaces</h3>
 * <p>
 * Note that there is some overlap between this feature and <a href="http://ocpsoft.org/prettyfaces">PrettyFaces</a>.
 * The difference is that FacesViews has a focus on zero- or very minimal config, where PrettyFaces has a focus on very
 * powerful mapping mechanisms, which of course need some level of configuration. As such FacesViews will only focus on
 * auto discovering views and mapping them to both <code>.xhtml</code> and to no-extension without needing to explicitly
 * declare the <code>FacesServlet</code> in <code>web.xml</code>.
 * <p>
 * Specifically, FacesViews will thus <em>not</em> become a general URL rewriting tool (e.g. one that maps path segments
 * to parameters, or that totally changes the name of the URL). For this the user is advised to look at the
 * aforementioned <a href="http://ocpsoft.org/prettyfaces">PrettyFaces</a>.
 *
 * @author Arjan Tijms
 * @see FacesViewsResolver
 * @see FacesViewsForwardingFilter
 * @see ExtensionAction
 * @see PathAction
 * @see UriExtensionRequestWrapper
 * @see FacesViewsViewHandlerInstaller
 * @see FacesViewsViewHandler
 * @see ViewHandlerMode
 */
public final class FacesViews {


	// Defaults -------------------------------------------------------------------------------------------------------

	/**
	 * A special dedicated "well-known" directory where facelets implementing views can be placed.
	 * This directory is scanned by convention so that no explicit configuration is needed.
	 */
	public static final String WEB_INF_VIEWS = "/WEB-INF/faces-views/";


	// Context parameter names ----------------------------------------------------------------------------------------

	/**
	 * The name of the boolean context parameter to switch auto-scanning completely off for Servlet 3.0 containers.
	 */
	public static final String FACES_VIEWS_ENABLED_PARAM_NAME = "org.omnifaces.FACES_VIEWS_ENABLED";

	/**
	 * The name of the commaseparated context parameter where the value holds a comma separated list of paths that are
	 * to be scanned by faces views.
	 */
	public static final String FACES_VIEWS_SCAN_PATHS_PARAM_NAME = "org.omnifaces.FACES_VIEWS_SCAN_PATHS";

	/**
	 * The name of the boolean context parameter via which the user can set scanned views to be always rendered
	 * extensionless. Without this setting (or it being set to false), it depends on whether the request URI uses an
	 * extension or not. If it doesn't, links are also rendered without one, otherwise are rendered with an extension.
	 */
	public static final String FACES_VIEWS_SCANNED_VIEWS_EXTENSIONLESS_PARAM_NAME = "org.omnifaces.FACES_VIEWS_SCANNED_VIEWS_ALWAYS_EXTENSIONLESS";

	/**
	 * The name of the enum context parameter that determines the action that is performed whenever a resource
	 * is requested WITH extension that's also available without an extension. See {@link ExtensionAction}
	 * @see ExtensionAction
	 */
	public static final String FACES_VIEWS_EXTENSION_ACTION_PARAM_NAME = "org.omnifaces.FACES_VIEWS_EXTENSION_ACTION";

	/**
	 * The name of the enum context parameter that determines the action that is performed whenever a resource
	 * is requested in a public path that has been used for scanning views by faces views. See {@link PathAction}
	 * @see PathAction
	 */
	public static final String FACES_VIEWS_PATH_ACTION_PARAM_NAME = "org.omnifaces.FACES_VIEWS_PATH_ACTION";

	/**
	 * The name of the enum context parameter that determines the method used by FacesViews to invoke the FacesServlet.
	 * See {@link FacesServletDispatchMethod}.
	 * @see FacesServletDispatchMethod
	 * @deprecated Since 2.6 As this is superfluous since Servlet 3.0.
	 * It will default to DO_FILTER and automatically use FORWARD when resource is not mapped.
	 */
	@Deprecated // TODO: remove in OmniFaces 3.0.
	public static final String FACES_VIEWS_DISPATCH_METHOD_PARAM_NAME = "org.omnifaces.FACES_VIEWS_DISPATCH_METHOD";

	/**
	 * The name of the boolean context parameter via which the user can set whether the
	 * {@link FacesViewsForwardingFilter} should match before declared filters (false) or after declared filters (true).
	 */
	public static final String FACES_VIEWS_FILTER_AFTER_DECLARED_FILTERS_PARAM_NAME = "org.omnifaces.FACES_VIEWS_FILTER_AFTER_DECLARED_FILTERS";

	/**
	 * The name of the enum context parameter via which the user can set whether the {@link FacesViewsViewHandler}
	 * should strip the extension from the parent view handler's outcome or construct the URL itself and only take the
	 * query parameters (if any) from the parent.
	 * @see ViewHandlerMode
	 * @deprecated Since 2.6 As this is superfluous since Servlet 3.0.
	 */
	@Deprecated // TODO: remove in OmniFaces 3.0.
	public static final String FACES_VIEWS_VIEW_HANDLER_MODE_PARAM_NAME = "org.omnifaces.FACES_VIEWS_VIEW_HANDLER_MODE";


	// Request attributes ---------------------------------------------------------------------------------------------

	/**
	 * The name of the request attribute under which the original request servlet path is stored.
	 */
	public static final String FACES_VIEWS_ORIGINAL_SERVLET_PATH = "org.omnifaces.facesviews.original.servlet_path";

	/**
	 * The name of the request attribute under which the original request path info is stored.
	 */
	public static final String FACES_VIEWS_ORIGINAL_PATH_INFO = "org.omnifaces.facesviews.original.path_info";


	// Constants ------------------------------------------------------------------------------------------------------

	private static final String[] RESTRICTED_DIRECTORIES = { "/WEB-INF/", "/META-INF/", "/resources/" };

	// TODO: those should be properties of an @ApplicationScoped bean.
	private static final String SCAN_PATHS = "org.omnifaces.facesviews.scan_paths";
	private static final String PUBLIC_SCAN_PATHS = "org.omnifaces.facesviews.public_scan_paths";
	private static final String MULTIVIEWS_PATHS = "org.omnifaces.facesviews.multiviews_paths";
	private static final String FACES_SERVLET_EXTENSIONS = "org.omnifaces.facesviews.faces_servlet_extensions";
	private static final String MAPPED_RESOURCES = "org.omnifaces.facesviews.mapped_resources";
	private static final String REVERSE_MAPPED_RESOURCES = "org.omnifaces.facesviews.reverse_mapped_resources";
	private static final String MULTIVIEWS_RESOURCES = "org.omnifaces.facesviews.multiviews_resources";
	private static final String EXCLUDED_PATHS = "org.omnifaces.facesviews.exclude_paths";
	private static final String ENCOUNTERED_EXTENSIONS = "org.omnifaces.facesviews.encountered_extensions";
	private static final String MAPPED_WELCOME_FILES = "org.omnifaces.facesviews.mapped_welcome_files";
	private static final String MULTIVIEWS_WELCOME_FILE = "org.omnifaces.facesviews.multiviews_welcome_file";

	private static Boolean facesViewsEnabled;
	private static Boolean multiViewsEnabled;

	private FacesViews() {
		//
	}


	// Initialization -------------------------------------------------------------------------------------------------

	/**
	 * This will register the {@link FacesViewsForwardingFilter}.
	 * This is invoked by {@link ApplicationInitializer}.
	 * @param servletContext The involved servlet context.
	 */
	public static void registerForwardingFilter(ServletContext servletContext) {
		if (!isFacesViewsEnabled(servletContext)) {
			return;
		}

		// First scan welcome files in web.xml.
		scanAndStoreWelcomeFiles(servletContext);

		// Scan our dedicated directory for Faces resources that need to be mapped.
		Map<String, String> collectedViews = scanAndStoreViews(servletContext, true);

		if (collectedViews.isEmpty()) {
			return;
		}

		// Register a Filter that forwards extensionless requests to an extension mapped request, e.g. /index to /index.xhtml
		// The FacesServlet doesn't work well with the exact mapping that we use for extensionless URLs.
		FilterRegistration filterRegistration = servletContext.addFilter(FacesViewsForwardingFilter.class.getName(), FacesViewsForwardingFilter.class);

		// Register a Facelets resource resolver that resolves requests like /index.xhtml to /WEB-INF/faces-views/index.xhtml
		// TODO: Migrate ResourceResolver to ResourceHandler.
		servletContext.setInitParameter(FACELETS_RESOURCE_RESOLVER_PARAM_NAME, FacesViewsResolver.class.getName());

		addForwardingFilterMappings(servletContext, collectedViews, filterRegistration);

		// We now need to map the Faces Servlet to the extensions we found,
		// but at this point in time this Faces Servlet might not be created yet,
		// so we do this part in the FacesViews#addFacesServletMappings() method below,
		// which is called from ApplicationListener#contextInitialized() later.
	}

	private static void addForwardingFilterMappings(ServletContext servletContext, Map<String, String> collectedViews, FilterRegistration filterRegistration) {
		boolean filterAfterDeclaredFilters = parseBoolean(servletContext.getInitParameter(FACES_VIEWS_FILTER_AFTER_DECLARED_FILTERS_PARAM_NAME));

		if (hasMultiViewsWelcomeFile(servletContext)) {
			// When MultiViews is enabled and there are mapped welcome files, we need to filter on /* otherwise path params won't work on root.
			filterRegistration.addMappingForUrlPatterns(EnumSet.of(REQUEST, FORWARD), filterAfterDeclaredFilters, "/*");
		}
		else {
			// Map the forwarding filter to all the resources we found.
			for (String mapping : collectedViews.keySet()) {
				filterRegistration.addMappingForUrlPatterns(EnumSet.of(REQUEST, FORWARD), filterAfterDeclaredFilters, mapping);
			}

			// Additionally map the filter to all paths that were scanned and which are also directly accessible.
			// This is to give the filter an opportunity to block these.
			for (String path : getPublicRootPaths(servletContext)) {
				filterRegistration.addMappingForUrlPatterns(null, false, path + "*");
			}
		}
	}

	/**
	 * This will map the {@link FacesServlet} to extensions found during scanning in {@link ApplicationInitializer}.
	 * This is invoked by {@link ApplicationListener}, because the {@link FacesServlet} has to be available.
	 * @param servletContext The involved servlet context.
	 */
	public static void addFacesServletMappings(ServletContext servletContext) {
		if (!isFacesViewsEnabled(servletContext)) {
			return;
		}

		Set<String> encounteredExtensions = getEncounteredExtensions(servletContext);

		if (isEmpty(encounteredExtensions)) {
			return;
		}

		Set<String> mappings = new HashSet<>(encounteredExtensions);
		mappings.addAll(getMappedWelcomeFiles(servletContext));
		mappings.addAll(filterExtension(getMappedResources(servletContext).keySet()));

		if (getFacesServletDispatchMethod(servletContext) == DO_FILTER) {
			// In order for the DO_FILTER method to work the FacesServlet, in addition the forward filter,
			// has to be mapped on all extensionless resources.
			mappings.addAll(filterExtension(getMappedResources(servletContext).keySet()));
		}

		ServletRegistration facesServletRegistration = getFacesServletRegistration(servletContext);

		if (facesServletRegistration != null) {
			Collection<String> existingMappings = facesServletRegistration.getMappings();

			for (String mapping : mappings) {
				if (!existingMappings.contains(mapping)) {
					facesServletRegistration.addMapping(mapping);
				}
			}
		}
	}

	/**
	 * Register a view handler that transforms a view id with extension back to an extensionless one.
	 * This is invoked by {@link FacesViewsViewHandlerInstaller}, because the {@link Application} has to be available.
	 * @param servletContext The involved servlet context.
	 */
	public static void registerViewHander(ServletContext servletContext) {
		if (isFacesViewsEnabled(servletContext) && !isEmpty(getEncounteredExtensions(servletContext))) {
			Application application = getApplicationFromFactory();
			application.setViewHandler(new FacesViewsViewHandler(application.getViewHandler()));
		}
	}


	// Scanning -------------------------------------------------------------------------------------------------------

	/**
	 * Scans for faces-views resources recursively.
	 *
	 * @param servletContext The involved servlet context.
	 * @return The views found during scanning, or an empty map if no views encountered.
	 */
	static Map<String, String> scanAndStoreViews(ServletContext servletContext, boolean collectExtensions) {
		Map<String, String> collectedViews = new HashMap<>();
		Set<String> collectedExtensions = new HashSet<>();
		Set<String> excludedPaths = new HashSet<>();

		for (String[] rootPathAndExtension : getRootPathsAndExtensions(servletContext)) {
			String rootPath = rootPathAndExtension[0];

			if (isExcludePath(rootPath)) {
				excludedPaths.add(rootPath.substring(1));
			}
			else {
				String extension = rootPathAndExtension[1];
				scanViews(servletContext, rootPath, servletContext.getResourcePaths(rootPath), collectedViews, extension, collectedExtensions);
			}
		}

		for (String collectedView : new HashSet<>(collectedViews.keySet())) {
			for (String excludedPath : excludedPaths) {
				if (collectedView.startsWith(excludedPath)) {
					collectedViews.remove(collectedView);
				}
			}
		}

		if (!collectedViews.isEmpty()) {
			servletContext.setAttribute(MAPPED_RESOURCES, unmodifiableMap(collectedViews));
			Map<String, String> reverseMappedResources = new HashMap<>();
			Set<String> multiViewsResources = new HashSet<>();
			for (Entry<String, String> collectedView : collectedViews.entrySet()) {
				if (ResourcePaths.isExtensionless(collectedView.getKey())) {
					reverseMappedResources.put(collectedView.getKey(), collectedView.getValue());
				}
				if (collectedView.getKey().endsWith("/*")) {
					multiViewsResources.add(collectedView.getKey().substring(0, collectedView.getKey().length() - 2));
				}
			}
			servletContext.setAttribute(REVERSE_MAPPED_RESOURCES, unmodifiableMap(reverseMappedResources));
			servletContext.setAttribute(MULTIVIEWS_RESOURCES, unmodifiableSet(multiViewsResources));
			servletContext.setAttribute(EXCLUDED_PATHS, unmodifiableSet(excludedPaths));

			if (collectExtensions) {
				storeExtensions(servletContext, collectedViews, collectedExtensions);
			}
		}

		return collectedViews;
	}

	private static void scanAndStoreWelcomeFiles(ServletContext servletContext) {
		URL webXml;

		try {
			webXml = servletContext.getResource("/WEB-INF/web.xml");
		}
		catch (MalformedURLException e) {
			throw new IllegalStateException(e);
		}

		Set<String> mappedWelcomeFiles = new LinkedHashSet<>();

		for (String welcomeFile : getNodeTextContents(webXml, "welcome-file-list/welcome-file")) {
			if (isExtensionless(welcomeFile)) {
				if (!welcomeFile.startsWith("/")) {
					welcomeFile = "/" + welcomeFile;
				}

				mappedWelcomeFiles.add(stripTrailingSlash(welcomeFile));
			}
		}

		servletContext.setAttribute(MAPPED_WELCOME_FILES, unmodifiableSet(mappedWelcomeFiles));
	}

	@SuppressWarnings("unchecked")
	private static Set<String[]> getRootPathsAndExtensions(ServletContext servletContext) {
		Set<String[]> rootPaths = (Set<String[]>) servletContext.getAttribute(SCAN_PATHS);

		if (rootPaths == null) {
			rootPaths = new HashSet<>();
			rootPaths.add(new String[] { WEB_INF_VIEWS, null });
			Set<String> multiViewsPaths = new TreeSet<>(Collator.getInstance(ENGLISH)); // Makes sure ! is sorted before /.

			for (String rootPath : csvToList(servletContext.getInitParameter(FACES_VIEWS_SCAN_PATHS_PARAM_NAME))) {
				boolean multiViews = rootPath.endsWith("/*");

				if (multiViews) {
					rootPath = rootPath.substring(0, rootPath.lastIndexOf("/*"));
				}

				String[] rootPathAndExtension = rootPath.contains("*") ? rootPath.split(quote("*")) : new String[] { rootPath, null };
				rootPathAndExtension[0] = normalizeRootPath(rootPathAndExtension[0]);
				rootPaths.add(rootPathAndExtension);

				if (multiViews) {
					multiViewsPaths.add(rootPathAndExtension[0]);
				}
			}

			servletContext.setAttribute(SCAN_PATHS, unmodifiableSet(rootPaths));
			servletContext.setAttribute(MULTIVIEWS_PATHS, unmodifiableSet(multiViewsPaths));
		}

		return rootPaths;
	}

	private static void storeExtensions(ServletContext servletContext, Map<String, String> collectedViews, Set<String> collectedExtensions) {
		servletContext.setAttribute(ENCOUNTERED_EXTENSIONS, unmodifiableSet(collectedExtensions));

		if (!collectedExtensions.isEmpty()) {
			for (String welcomeFile : getMappedWelcomeFiles(servletContext)) {
				if (isMultiViewsEnabled(servletContext) && collectedViews.containsKey(welcomeFile + "/*")) {
					servletContext.setAttribute(MULTIVIEWS_WELCOME_FILE, welcomeFile);
				}
			}
		}
	}

	/**
	 * A public path is a path that is also directly accessible, e.g. is world readable.
	 * This excludes the special path /, which is by definition world readable but not included in this set.
	 */
	@SuppressWarnings("unchecked")
	private static Set<String> getPublicRootPaths(ServletContext servletContext) {
		Set<String> publicRootPaths = (Set<String>) servletContext.getAttribute(PUBLIC_SCAN_PATHS);

		if (publicRootPaths == null) {
			publicRootPaths = new HashSet<>();

			for (String[] rootPathAndExtension : getRootPathsAndExtensions(servletContext)) {
				String rootPath = rootPathAndExtension[0];

				if (!"/".equals(rootPath) && !isExcludePath(rootPath) && !startsWithOneOf(rootPath, RESTRICTED_DIRECTORIES)) {
					publicRootPaths.add(rootPath);
				}
			}

			servletContext.setAttribute(PUBLIC_SCAN_PATHS, unmodifiableSet(publicRootPaths));
		}

		return publicRootPaths;
	}

	/**
	 * Scans resources (views) recursively starting with the given resource paths for a specific root path, and collects
	 * those and all unique extensions encountered in a flat map respectively set.
	 *
	 * @param servletContext The involved servlet context.
	 * @param rootPath One of the paths from which views are scanned. By default this is typically /WEB-INF/faces-view/
	 * @param resourcePaths Collection of paths to be considered for scanning, can be either files or directories.
	 * @param collectedViews A mapping of all views encountered during scanning. Mapping will be from the simplified
	 * form to the actual location relatively to the web root. E.g key "foo", value "/WEB-INF/faces-view/foo.xhtml"
	 * @param extensionToScan A specific extension to scan for. Should start with a ., e.g. ".xhtml". If this is given,
	 * only resources with that extension will be scanned. If null, all resources will be scanned.
	 * @param collectedExtensions Set in which all unique extensions will be collected. May be null, in which case no
	 * extensions will be collected.
	 */
	private static void scanViews(ServletContext servletContext, String rootPath, Set<String> resourcePaths,
			Map<String, String> collectedViews, String extensionToScan, Set<String> collectedExtensions)
	{
		if (isEmpty(resourcePaths)) {
			return;
		}

		boolean hasMultiViewsWelcomeFile = hasMultiViewsWelcomeFile(servletContext);

		for (String resourcePath : resourcePaths) {
			if (isDirectory(resourcePath)) {
				if (canScanDirectory(rootPath, resourcePath)) {
					scanViews(servletContext, rootPath, servletContext.getResourcePaths(resourcePath), collectedViews, extensionToScan, collectedExtensions);
				}
			}
			else if (canScanResource(resourcePath, extensionToScan)) {
				scanView(servletContext, rootPath, resourcePath, collectedViews, collectedExtensions, hasMultiViewsWelcomeFile);
			}
		}
	}

	private static void scanView(ServletContext servletContext, String rootPath, String resourcePath,
			Map<String, String> collectedViews, Set<String> collectedExtensions, boolean hasMultiViewsWelcomeFile)
	{
		// Strip the root path from the current path.
		// E.g. /WEB-INF/faces-views/foo.xhtml will become foo.xhtml if the root path = /WEB-INF/faces-view/
		String resource = stripPrefixPath(rootPath, resourcePath);

		// Store the resource with and without an extension, e.g. store both foo.xhtml and foo
		collectedViews.put(resource, resourcePath);
		String extensionlessResource = stripExtension(resource);
		String extensionlessResourcePath = stripExtension(resourcePath);

		if (isMultiViewsResource(servletContext, extensionlessResourcePath)) {
			collectedViews.put(extensionlessResource + "/*", resourcePath);
		}
		else {
			if (hasMultiViewsWelcomeFile) { // This will install forwarding filter on /* and therefore we need to cover / ourselves.
				collectedViews.put(extensionlessResource + "/", resourcePath);
			}

			collectedViews.put(extensionlessResource, resourcePath);

			// If FacesServlet is explicitly mapped on virtual extensions (e.g. when FacesViews is later enabled on a legacy app with *.jsf),
			// then we need to collect them as well so that these can properly be 301-redirected to extensionless one.
			for (String facesServletExtension : getFacesServletExtensions(servletContext)) {
				if (!resourcePath.endsWith(facesServletExtension)) {
					collectedViews.put(extensionlessResource + facesServletExtension, resourcePath);
				}
			}
		}

		// Optionally, collect all unique extensions that we have encountered.
		if (collectedExtensions != null) {
			collectedExtensions.add("*" + getExtension(resourcePath));
		}
	}

	private static String normalizeRootPath(String rootPath) {
		String normalizedPath = rootPath;
		boolean excludePath = isExcludePath(rootPath);

		if (!normalizedPath.substring(excludePath ? 1 : 0).startsWith("/")) {
			normalizedPath = "/" + (excludePath ? "!" : "") + normalizedPath;
		}

		if (!normalizedPath.endsWith("/")) {
			normalizedPath = normalizedPath + "/";
		}

		return normalizedPath;
	}

	private static boolean isExcludePath(String rootPath) {
		return rootPath.charAt(0) == '!';
	}

	private static boolean canScanDirectory(String rootPath, String directory) {
		if (!"/".equals(rootPath)) {
			// If a user has explicitly asked for scanning anything other than /, every sub directory of it can be scanned.
			return true;
		}

		// For the special root path /, don't scan /WEB-INF, /META-INF and /resources directories.
		return !startsWithOneOf(directory, RESTRICTED_DIRECTORIES);
	}

	private static boolean canScanResource(String resource, String extensionToScan) {

		// If no extension has been explicitly defined, we scan all extensions encountered.
		return (extensionToScan == null) || resource.endsWith(extensionToScan);
	}

	private static boolean isMultiViewsResource(ServletContext servletContext, String resource) {
		Set<String> multiviewsPaths = getApplicationAttribute(servletContext, MULTIVIEWS_PATHS);

		if (multiviewsPaths != null) {
			String path = resource + "/";

			for (String multiviewsPath : multiviewsPaths) {
				if (path.startsWith(multiviewsPath)) {
					return true;
				}
			}
		}

		return false;
	}

	private static boolean hasMultiViewsWelcomeFile(ServletContext servletContext) {
		return isMultiViewsEnabled(servletContext) && !getMappedWelcomeFiles(servletContext).isEmpty();
	}


	// Helpers for FacesViewsForwardingFilter -------------------------------------------------------------------------

	static ExtensionAction getExtensionAction(ServletContext servletContext) {
		return getEnumInitParameter(servletContext, FACES_VIEWS_EXTENSION_ACTION_PARAM_NAME, ExtensionAction.class, REDIRECT_TO_EXTENSIONLESS);
	}

	static PathAction getPathAction(ServletContext servletContext) {
		return getEnumInitParameter(servletContext, FACES_VIEWS_PATH_ACTION_PARAM_NAME, PathAction.class, SEND_404);
	}

	static FacesServletDispatchMethod getFacesServletDispatchMethod(ServletContext servletContext) {
		return getEnumInitParameter(servletContext, FACES_VIEWS_DISPATCH_METHOD_PARAM_NAME, FacesServletDispatchMethod.class, DO_FILTER);
	}

	static boolean isResourceInPublicPath(ServletContext servletContext, String resource) {
		for (String path : getPublicRootPaths(servletContext)) {
			if (resource.startsWith(path)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Obtains the full request URL from the given request and the given resource complete with the query string,
	 * but with the extension (if any) cut out.
	 * E.g. <code>http://localhost/foo/bar.xhtml?kaz=1</code> becomes <code>http://localhost/foo/bar?kaz=1</code>
	 */
	static String getExtensionlessURLWithQuery(HttpServletRequest request, String resource) {
		String queryString = (request.getQueryString() == null) ? "" : ("?" + request.getQueryString());
		String baseURL = getRequestBaseURL(request);
		return baseURL.substring(0, baseURL.length() - 1) + stripExtension(resource) + queryString;
	}

	static String getMultiViewsWelcomeFile(ServletContext servletContext, Map<String, String> resources, String normalizedServletPath) {
		Path path = Paths.get(normalizedServletPath).getParent();

		if (path != null) {
			Set<String> mappedWelcomeFiles = getMappedWelcomeFiles(servletContext);

			for (; path.getParent() != null; path = path.getParent()) {
				for (String mappedWelcomeFile : mappedWelcomeFiles) {
					String subfolderWelcomeFile = path.toString() + mappedWelcomeFile;

					if (resources.containsKey(subfolderWelcomeFile + "/*")) {
						return subfolderWelcomeFile;
					}
				}
			}
		}

		return getMultiViewsWelcomeFile(servletContext);
	}


	// Helpers for FacesViewsViewHandler ------------------------------------------------------------------------------

	static boolean isScannedViewsAlwaysExtensionless(ServletContext servletContext) {
		String alwaysExtensionless = servletContext.getInitParameter(FACES_VIEWS_SCANNED_VIEWS_EXTENSIONLESS_PARAM_NAME);
		return isEmpty(alwaysExtensionless) || parseBoolean(alwaysExtensionless);
	}

	static ViewHandlerMode getViewHandlerMode(ServletContext servletContext) {
		return getEnumInitParameter(servletContext, FACES_VIEWS_VIEW_HANDLER_MODE_PARAM_NAME, ViewHandlerMode.class, STRIP_EXTENSION_FROM_PARENT);
	}

	@SuppressWarnings("unchecked")
	static Set<String> getFacesServletExtensions(ServletContext servletContext) {
		Set<String> extensions = (Set<String>) servletContext.getAttribute(FACES_SERVLET_EXTENSIONS);

		if (extensions == null) {
			extensions = new HashSet<>();

			for (String mapping : getFacesServletMappings(servletContext)) {
				if (mapping.startsWith("*")) {
					extensions.add(mapping.substring(1));
				}
			}

			servletContext.setAttribute(FACES_SERVLET_EXTENSIONS, unmodifiableSet(extensions));
		}

		return extensions;
	}


	// Helpers for FacesViewsResolver ---------------------------------------------------------------------------------

	static String getMappedPath(String path) {
		Map<String, String> mappedResources = getMappedResources(getServletContext());
		return (mappedResources != null && mappedResources.containsKey(path)) ? mappedResources.get(path) : path;
	}


	// Internal helpers -----------------------------------------------------------------------------------------------

	private static <E extends Enum<E>> E getEnumInitParameter(ServletContext servletContext, String name, Class<E> type, E defaultValue) {
		String value = servletContext.getInitParameter(name);

		if (isEmpty(value)) {
			return defaultValue;
		}

		try {
			return Enum.valueOf(type, value.toUpperCase(US));
		}
		catch (Exception e) {
			throw new IllegalArgumentException(format("Value '%s' is not valid for context parameter '%s'", value, name), e);
		}
	}

	static Set<String> getMultiViewsPaths(ServletContext servletContext) {
		return getApplicationAttribute(servletContext, MULTIVIEWS_PATHS);
	}

	static Map<String, String> getMappedResources(ServletContext servletContext) {
		return getApplicationAttribute(servletContext, MAPPED_RESOURCES);
	}

	static Map<String, String> getReverseMappedResources(ServletContext servletContext) {
		return getApplicationAttribute(servletContext, REVERSE_MAPPED_RESOURCES);
	}

	static Set<String> getMultiViewsResources(ServletContext servletContext) {
		return getApplicationAttribute(servletContext, MULTIVIEWS_RESOURCES);
	}

	static Set<String> getExcludedPaths(ServletContext servletContext) {
		return getApplicationAttribute(servletContext, EXCLUDED_PATHS);
	}

	static Set<String> getEncounteredExtensions(ServletContext servletContext) {
		return getApplicationAttribute(servletContext, ENCOUNTERED_EXTENSIONS);
	}

	static Set<String> getMappedWelcomeFiles(ServletContext servletContext) {
		return getApplicationAttribute(servletContext, MAPPED_WELCOME_FILES);
	}

	static String getMultiViewsWelcomeFile(ServletContext servletContext) {
		return getApplicationAttribute(servletContext, MULTIVIEWS_WELCOME_FILE);
	}


	// Utility --------------------------------------------------------------------------------------------------------

	/**
	 * Returns whether FacesViews feature is enabled. That is, when the <code>org.omnifaces.FACES_VIEWS_ENABLED</code>
	 * context parameter value does not equal <code>false</code>.
	 * @param servletContext The involved servlet context.
	 * @return Whether FacesViews feature is enabled.
	 * @since 2.5
	 */
	public static boolean isFacesViewsEnabled(ServletContext servletContext) {
		if (facesViewsEnabled == null) {
			facesViewsEnabled = !"false".equals(servletContext.getInitParameter(FACES_VIEWS_ENABLED_PARAM_NAME));
		}

		return facesViewsEnabled;
	}

	/**
	 * Returns whether MultiViews feature is enabled. This is implicitly enabled when
	 * <code>org.omnifaces.FACES_VIEWS_SCAN_PATHS</code> context parameter value is suffixed with <code>/*</code>.
	 * @param servletContext The involved servlet context.
	 * @return Whether MultiViews feature is enabled.
	 * @since 2.5
	 */
	public static boolean isMultiViewsEnabled(ServletContext servletContext) {
		if (multiViewsEnabled == null) {
			multiViewsEnabled = !isEmpty(getMultiViewsPaths(servletContext));
		}

		return multiViewsEnabled;
	}

	/**
	 * Returns whether MultiViews feature is enabled on given request.
	 * @param request The involved HTTP servlet request.
	 * @return Whether MultiViews feature is enabled on given request.
	 * @since 2.6
	 */
	public static boolean isMultiViewsEnabled(HttpServletRequest request) {
		String resource = request.getServletPath();

		if (isEmpty(resource) && request.getPathInfo() != null) {
			resource = request.getPathInfo();
		}

		return isMultiViewsEnabled(request.getServletContext(), resource);
	}

	private static boolean isMultiViewsEnabled(ServletContext servletContext, String resource) {
		if (!isMultiViewsEnabled(servletContext)) {
			return false;
		}

		Set<String> excludedPaths = getExcludedPaths(servletContext);

		if (!isEmpty(excludedPaths)) {
			String path = resource + "/";

			for (String excludedPath : excludedPaths) {
				if (path.startsWith(excludedPath)) {
					return false;
				}
			}
		}

		Set<String> multiViewsResources = getMultiViewsResources(servletContext);

		if (multiViewsResources != null && multiViewsResources.contains(resource)) {
			return true;
		}

		return getMultiViewsWelcomeFile(servletContext) != null;
	}

	/**
	 * Strips any mapped welcome file prefix path from the given resource.
	 * @param servletContext The involved servlet context.
	 * @param resource The resource.
	 * @return The resource without the welcome file prefix path, or as-is if it didn't start with this prefix.
	 * @since 2.5
	 */
	public static String stripWelcomeFilePrefix(ServletContext servletContext, String resource) {
		for (String mappedWelcomeFile : getMappedWelcomeFiles(servletContext)) {
			if (resource.endsWith(mappedWelcomeFile)) {
				return resource.substring(0, resource.length() - mappedWelcomeFile.length()) + "/";
			}
		}

		return resource;
	}

	/**
	 * Strips any special '/WEB-INF/faces-views' prefix path from the given resource.
	 * @param resource The resource.
	 * @return The resource without the special prefix path, or as-is if it didn't start with this prefix.
	 */
	public static String stripFacesViewsPrefix(String resource) {
		return stripPrefixPath(WEB_INF_VIEWS, resource);
	}

}