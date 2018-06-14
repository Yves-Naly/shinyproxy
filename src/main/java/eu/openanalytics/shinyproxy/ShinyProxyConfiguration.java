package eu.openanalytics.shinyproxy;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.function.IntPredicate;

import javax.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import eu.openanalytics.containerproxy.backend.strategy.IProxyTestStrategy;
import eu.openanalytics.containerproxy.model.runtime.Proxy;

@Configuration
public class ShinyProxyConfiguration {

	private Logger log = LogManager.getLogger(ShinyProxyConfiguration.class);
	
	@Inject
	private Environment environment;
	
	@Bean
	@Primary
	public IProxyTestStrategy getTargetTestStrategy() {
		
		return new IProxyTestStrategy() {
			@Override
			public boolean testProxy(Proxy proxy) {
				
				int totalWaitMs = Integer.parseInt(environment.getProperty("proxy.container-wait-time", "20000"));
				int waitMs = Math.min(2000, totalWaitMs);
				int maxTries = totalWaitMs / waitMs;
				int timeoutMs = Integer.parseInt(environment.getProperty("proxy.container-wait-timeout", "5000"));
				
				if (proxy.getTargets().isEmpty()) return false;
				URI targetURI = proxy.getTargets().values().iterator().next();
				
				return retry(i -> {
					try {
						URL testURL = new URL(targetURI.toString());
						HttpURLConnection connection = ((HttpURLConnection) testURL.openConnection());
						connection.setConnectTimeout(timeoutMs);
						int responseCode = connection.getResponseCode();
						if (responseCode == 200) return true;
					} catch (Exception e) {
						if (i > 1 && log != null) log.warn(String.format("Container unresponsive, trying again (%d/%d): %s", i, maxTries, targetURI));
					}
					return false;
				}, maxTries, waitMs, false);
			}
		};
	}
	
	private static boolean retry(IntPredicate job, int tries, int waitTime, boolean retryOnException) {
		boolean retVal = false;
		RuntimeException exception = null;
		for (int currentTry = 1; currentTry <= tries; currentTry++) {
			try {
				if (job.test(currentTry)) {
					retVal = true;
					exception = null;
					break;
				}
			} catch (RuntimeException e) {
				if (retryOnException) exception = e;
				else throw e;
			}
			try { Thread.sleep(waitTime); } catch (InterruptedException ignore) {}
		}
		if (exception == null) return retVal;
		else throw exception;
	}
}