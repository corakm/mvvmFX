/*******************************************************************************
 * Copyright 2013 Alexander Casall
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package de.saxsys.mvvmfx.utils.notifications;

import de.saxsys.mvvmfx.ViewModel;
import de.saxsys.mvvmfx.testingutils.jfxrunner.JfxRunner;
import javafx.application.Platform;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JfxRunner.class)
public class DefaultNotificationCenterTest {
	
	private static final String TEST_NOTIFICATION = "test_notification";
	private static final String TEST_NOTIFICATION_2 = TEST_NOTIFICATION + "shouldnotget";
	private static final Object[] OBJECT_ARRAY_FOR_NOTIFICATION = new String[] { "test" };
	
	private NotificationCenter defaultCenter;
	
	DummyNotificationObserver observer1;
	DummyNotificationObserver observer2;
	DummyNotificationObserver observer3;
	
	@Before
	public void init() {
		observer1 = Mockito.mock(DummyNotificationObserver.class);
		observer2 = Mockito.mock(DummyNotificationObserver.class);
		observer3 = Mockito.mock(DummyNotificationObserver.class);
		defaultCenter = new DefaultNotificationCenter();
	}
	
	@Test
	public void addObserverToDefaultNotificationCenterAndPostNotification() throws Exception {
		defaultCenter.subscribe(TEST_NOTIFICATION, observer1);
		defaultCenter.publish(TEST_NOTIFICATION);
		Mockito.verify(observer1).receivedNotification(TEST_NOTIFICATION);
	}
	
	@Test
	public void addObserverToDefaultNotificationCenterAndPostObjectNotification() throws Exception {
		defaultCenter.subscribe(TEST_NOTIFICATION, observer1);
		defaultCenter.publish(TEST_NOTIFICATION, OBJECT_ARRAY_FOR_NOTIFICATION);
		Mockito.verify(observer1).receivedNotification(TEST_NOTIFICATION, OBJECT_ARRAY_FOR_NOTIFICATION);
	}
	
	@Test
	public void addAndRemoveObserverToDefaultNotificationCenterAndPostNotification() throws Exception {
		defaultCenter.subscribe(TEST_NOTIFICATION, observer1);
		defaultCenter.subscribe(TEST_NOTIFICATION, observer2);
		defaultCenter.subscribe(TEST_NOTIFICATION, observer3);
		defaultCenter.unsubscribe(observer1);
		defaultCenter.unsubscribe(observer2);
		defaultCenter.publish(TEST_NOTIFICATION);
		Mockito.verify(observer1, Mockito.never()).receivedNotification(TEST_NOTIFICATION);
		Mockito.verify(observer2, Mockito.never()).receivedNotification(TEST_NOTIFICATION);
		Mockito.verify(observer3).receivedNotification(TEST_NOTIFICATION);
	}
	
	@Test
	public void addObserversToDefaultNotificationCenterAndPostNotification() throws Exception {
		defaultCenter.subscribe(TEST_NOTIFICATION, observer1);
		defaultCenter.subscribe(TEST_NOTIFICATION_2, observer2);
		defaultCenter.subscribe(TEST_NOTIFICATION, observer3);
		
		defaultCenter.publish(TEST_NOTIFICATION);
		Mockito.verify(observer1, Mockito.only()).receivedNotification(TEST_NOTIFICATION);
		Mockito.verify(observer2, Mockito.never()).receivedNotification(TEST_NOTIFICATION_2);
		Mockito.verify(observer3, Mockito.only()).receivedNotification(TEST_NOTIFICATION);
	}
	
	@Test
	public void addAndRemoveObserverForNameToDefaultNotificationCenterAndPostNotification() throws Exception {
		defaultCenter.subscribe(TEST_NOTIFICATION, observer1);
		defaultCenter.unsubscribe(TEST_NOTIFICATION, observer1);
		defaultCenter.publish(TEST_NOTIFICATION);
		Mockito.verify(observer1, Mockito.never()).receivedNotification(TEST_NOTIFICATION);
	}
	
	@Test
	public void subscribeSameObserverMultipleTimes() {
		defaultCenter.subscribe(TEST_NOTIFICATION, observer1);
		defaultCenter.subscribe(TEST_NOTIFICATION, observer1);
		
		defaultCenter.publish(TEST_NOTIFICATION);
		Mockito.verify(observer1, Mockito.times(2)).receivedNotification(TEST_NOTIFICATION);
	}
	
	@Test
	public void unsubscribeObserverThatWasSubscribedMultipleTimes() {
		defaultCenter.subscribe(TEST_NOTIFICATION, observer1);
		defaultCenter.subscribe(TEST_NOTIFICATION, observer1);
		defaultCenter.subscribe(TEST_NOTIFICATION, observer1);
		
		defaultCenter.unsubscribe(observer1);
		
		defaultCenter.publish(TEST_NOTIFICATION);
		Mockito.verify(observer1, Mockito.never()).receivedNotification(TEST_NOTIFICATION);
	}


	/**
	 * This is the same as {@link #unsubscribeObserverThatWasSubscribedMultipleTimes()} with the
	 * difference that here we use the overloaded unsubscribe method {@link NotificationCenter#unsubscribe(String, NotificationObserver)} that takes
	 * the message key as first parameter.
	 */
	@Test
	public void unsubscribeObserverThatWasSubscribedMultipleTimesViaMessageName() {
		defaultCenter.subscribe(TEST_NOTIFICATION, observer1);
		defaultCenter.subscribe(TEST_NOTIFICATION, observer1);
		defaultCenter.subscribe(TEST_NOTIFICATION, observer1);

		defaultCenter.unsubscribe(TEST_NOTIFICATION, observer1);

		defaultCenter.publish(TEST_NOTIFICATION);
		Mockito.verify(observer1, Mockito.never()).receivedNotification(TEST_NOTIFICATION);
	}


	/**
	 * In some use cases it's convenient to unregister an observer even if it wasn't subscribed yet.
	 * For example to prevent a duplicated subscription. 
	 * 
	 * This test case reproduces <a href="https://github.com/sialcasa/mvvmFX/issues/301">bug #301</a>.
	 */
	@Test
	public void removeObserverThatWasNotRegisteredYet() {
		defaultCenter.unsubscribe(TEST_NOTIFICATION, observer1);
	}

	@Test
	public void observerForViewModelIsCalledFromUiThread() throws InterruptedException, ExecutionException, TimeoutException {
		// Check that there is a UI-Thread available. This JUnit-Test isn't running on the UI-Thread but there needs to
		// be a UI-Thread available in the background.
		CompletableFuture<Void> uiThreadIsAvailable = new CompletableFuture<>();
		Platform.runLater(() -> uiThreadIsAvailable.complete(null));  // This would throw an IllegalStateException if no
		// UI-Thread is available.
		uiThreadIsAvailable.get(1l, TimeUnit.SECONDS);

		CompletableFuture<Boolean> future = new CompletableFuture<>();

		// The test doesn't run on the FX thread.
		assertThat(Platform.isFxApplicationThread()).isFalse();

		final ViewModel viewModel = Mockito.mock(ViewModel.class);
		defaultCenter.subscribe(viewModel, TEST_NOTIFICATION, (key, payload) -> {
			// the notification is executed on the FX thread.
			future.complete(Platform.isFxApplicationThread());
		});

		// view model publish() should be executed in the UI-thread
		defaultCenter.publish(viewModel, TEST_NOTIFICATION, new Object[]{});

		final Boolean wasCalledOnUiThread = future.get(1l, TimeUnit.SECONDS);

		assertThat(wasCalledOnUiThread).isTrue();
	}
	
	private class DummyNotificationObserver implements NotificationObserver {
		@Override
		public void receivedNotification(String key, Object... payload) {
			
		}
	}
	
}
