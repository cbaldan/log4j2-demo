package org.cbj.demo;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractOutputStreamAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.DirectFileRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.DirectWriteRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.PatternProcessor;
import org.apache.logging.log4j.core.appender.rolling.RollingFileManager;
import org.apache.logging.log4j.core.appender.rolling.RolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.TriggeringPolicy;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.core.net.Advertiser;

/**
 * This class is a new Log4j2 plugin, but in reality, it's a clone of the
 * provided {@link RollingFileAppender}.
 * <p>
 * It adds the follwing behavior:
 * <ul>
 * <li>The {@code filename} now can now have the same markers as
 * {@code filePattern}</li>
 * <li>The new boolean property {@code rolloverOnStartup} rolls all existing
 * files on startup of the application.</li>
 * </ul>
 */
@Plugin(name = "ImprovedRollingFileAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public final class ImprovedRollingFileAppender extends AbstractOutputStreamAppender<RollingFileManager> {

	private final String fileName;
	private final String filePattern;
	private Object advertisement;
	private final Advertiser advertiser;

	private ImprovedRollingFileAppender(final String name, final Layout<? extends Serializable> layout,
			final Filter filter, final RollingFileManager manager, final String fileName, final String filePattern,
			final boolean rolloverOnStartup, final boolean ignoreExceptions, final boolean immediateFlush,
			final Advertiser advertiser, final Property[] properties) {
		super(name, layout, filter, ignoreExceptions, immediateFlush, properties, manager);
		if (advertiser != null) {
			final Map<String, String> configuration = new HashMap<>(layout.getContentFormat());
			configuration.put("contentType", layout.getContentType());
			configuration.put("name", name);
			advertisement = advertiser.advertise(configuration);
		}
		this.fileName = fileName;
		this.filePattern = filePattern;
		this.advertiser = advertiser;

		if (rolloverOnStartup) {
			manager.rollover();
		}
	}

	/**
	 * Builds FileAppender instances.
	 *
	 * @param <B> The type to build
	 * @since 2.7
	 */
	public static class Builder<B extends Builder<B>> extends AbstractOutputStreamAppender.Builder<B>
			implements org.apache.logging.log4j.core.util.Builder<ImprovedRollingFileAppender> {

		@PluginBuilderAttribute
		private String fileName;

		@PluginBuilderAttribute
		@Required
		private String filePattern;

		@PluginBuilderAttribute
		@Required
		private boolean rolloverOnStartup;

		@PluginBuilderAttribute
		private boolean append = true;

		@PluginBuilderAttribute
		private boolean locking;

		@PluginElement("Policy")
		@Required
		private TriggeringPolicy policy;

		@PluginElement("Strategy")
		private RolloverStrategy strategy;

		@PluginBuilderAttribute
		private boolean advertise;

		@PluginBuilderAttribute
		private String advertiseUri;

		@PluginBuilderAttribute
		private boolean createOnDemand;

		@PluginBuilderAttribute
		private String filePermissions;

		@PluginBuilderAttribute
		private String fileOwner;

		@PluginBuilderAttribute
		private String fileGroup;

		@Override
		public ImprovedRollingFileAppender build() {
			// Even though some variables may be annotated with @Required, we must still
			// perform validation here for
			// call sites that build builders programmatically.
			final boolean isBufferedIo = isBufferedIo();
			final int bufferSize = getBufferSize();
			if (getName() == null) {
				LOGGER.error(ImprovedRollingFileAppender.class.getSimpleName() + " '{}': No name provided.", getName());
				return null;
			}

			if (!isBufferedIo && bufferSize > 0) {
				LOGGER.warn(
						ImprovedRollingFileAppender.class.getSimpleName()
								+ " '{}': The bufferSize is set to {} but bufferedIO is not true",
						getName(), bufferSize);
			}

			if (filePattern == null) {
				LOGGER.error(
						ImprovedRollingFileAppender.class.getSimpleName() + " '{}': No file name pattern provided.",
						getName());
				return null;
			}

			if (policy == null) {
				LOGGER.error(ImprovedRollingFileAppender.class.getSimpleName() + " '{}': No TriggeringPolicy provided.",
						getName());
				return null;
			}

			fileName = getFormattedFileName();

			if (strategy == null) {
				if (fileName != null) {
					strategy = DefaultRolloverStrategy.newBuilder()
							.withCompressionLevelStr(String.valueOf(Deflater.DEFAULT_COMPRESSION))
							.withConfig(getConfiguration()).build();
				} else {
					strategy = DirectWriteRolloverStrategy.newBuilder()
							.withCompressionLevelStr(String.valueOf(Deflater.DEFAULT_COMPRESSION))
							.withConfig(getConfiguration()).build();
				}
			} else if (fileName == null && !(strategy instanceof DirectFileRolloverStrategy)) {
				LOGGER.error(ImprovedRollingFileAppender.class.getSimpleName()
						+ " '{}': When no file name is provided a DirectFilenameRolloverStrategy must be configured",
						getName());
				return null;
			}

			final Layout<? extends Serializable> layout = getOrCreateLayout();
			final RollingFileManager manager = RollingFileManager.getFileManager(fileName, filePattern, append,
					isBufferedIo, policy, strategy, advertiseUri, layout, bufferSize, isImmediateFlush(),
					createOnDemand, filePermissions, fileOwner, fileGroup, getConfiguration());
			if (manager == null) {
				return null;
			}

			manager.initialize();

			return new ImprovedRollingFileAppender(getName(), layout, getFilter(), manager, fileName, filePattern,
					rolloverOnStartup, isIgnoreExceptions(), isImmediateFlush(),
					advertise ? getConfiguration().getAdvertiser() : null, getPropertyArray());
		}

		private String getFormattedFileName() {
			StringBuilder sb = new StringBuilder();

			DefaultRolloverStrategy rolloverStrategy = (DefaultRolloverStrategy) strategy;
			StrSubstitutor strSubstitutor = rolloverStrategy.getStrSubstitutor();
			PatternProcessor patternProcessor = new PatternProcessor(fileName);
			patternProcessor.formatFileName(strSubstitutor, sb, "");

			return sb.toString();
		}

		public String getAdvertiseUri() {
			return advertiseUri;
		}

		public String getFileName() {
			return fileName;
		}

		public boolean isAdvertise() {
			return advertise;
		}

		public boolean isAppend() {
			return append;
		}

		public boolean isCreateOnDemand() {
			return createOnDemand;
		}

		public boolean isLocking() {
			return locking;
		}

		public String getFilePermissions() {
			return filePermissions;
		}

		public String getFileOwner() {
			return fileOwner;
		}

		public String getFileGroup() {
			return fileGroup;
		}

		public B withAdvertise(final boolean advertise) {
			this.advertise = advertise;
			return asBuilder();
		}

		public B withAdvertiseUri(final String advertiseUri) {
			this.advertiseUri = advertiseUri;
			return asBuilder();
		}

		public B withAppend(final boolean append) {
			this.append = append;
			return asBuilder();
		}

		public B withFileName(final String fileName) {
			this.fileName = fileName;
			return asBuilder();
		}

		public B withCreateOnDemand(final boolean createOnDemand) {
			this.createOnDemand = createOnDemand;
			return asBuilder();
		}

		public B withLocking(final boolean locking) {
			this.locking = locking;
			return asBuilder();
		}

		public String getFilePattern() {
			return filePattern;
		}

		public TriggeringPolicy getPolicy() {
			return policy;
		}

		public RolloverStrategy getStrategy() {
			return strategy;
		}

		public B withFilePattern(final String filePattern) {
			this.filePattern = filePattern;
			return asBuilder();
		}

		public B withPolicy(final TriggeringPolicy policy) {
			this.policy = policy;
			return asBuilder();
		}

		public B withStrategy(final RolloverStrategy strategy) {
			this.strategy = strategy;
			return asBuilder();
		}

		public B withFilePermissions(final String filePermissions) {
			this.filePermissions = filePermissions;
			return asBuilder();
		}

		public B withFileOwner(final String fileOwner) {
			this.fileOwner = fileOwner;
			return asBuilder();
		}

		public B withFileGroup(final String fileGroup) {
			this.fileGroup = fileGroup;
			return asBuilder();
		}

	}

	@Override
	public boolean stop(final long timeout, final TimeUnit timeUnit) {
		setStopping();
		final boolean stopped = super.stop(timeout, timeUnit, false);
		if (advertiser != null) {
			advertiser.unadvertise(advertisement);
		}
		setStopped();
		return stopped;
	}

	/**
	 * Writes the log entry rolling over the file when required.
	 * 
	 * @param event The LogEvent.
	 */
	@Override
	public void append(final LogEvent event) {
		getManager().checkRollover(event);
		super.append(event);
	}

	/**
	 * Returns the File name for the Appender.
	 * 
	 * @return The file name.
	 */
	public String getFileName() {
		return fileName;
	}

	/**
	 * Returns the file pattern used when rolling over.
	 * 
	 * @return The file pattern.
	 */
	public String getFilePattern() {
		return filePattern;
	}

	/**
	 * Returns the triggering policy.
	 * 
	 * @param <T> TriggeringPolicy type
	 * @return The TriggeringPolicy
	 */
	public <T extends TriggeringPolicy> T getTriggeringPolicy() {
		return getManager().getTriggeringPolicy();
	}

	/**
	 * Creates a new Builder.
	 *
	 * @return a new Builder.
	 * @since 2.7
	 */
	@PluginBuilderFactory
	public static <B extends Builder<B>> B newBuilder() {
		return new Builder<B>().asBuilder();
	}
}
