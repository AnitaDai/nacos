/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.naming.consistency.ephemeral.distro;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.naming.BaseTest;
import com.alibaba.nacos.naming.cluster.transport.Serializer;
import com.alibaba.nacos.naming.consistency.ApplyAction;
import com.alibaba.nacos.naming.consistency.Datum;
import com.alibaba.nacos.naming.consistency.KeyBuilder;
import com.alibaba.nacos.naming.consistency.RecordListener;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.DistroProtocol;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.component.DistroComponentHolder;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.entity.DistroKey;
import com.alibaba.nacos.naming.consistency.ephemeral.distro.newimpl.task.DistroTaskEngineHolder;
import com.alibaba.nacos.naming.core.Instances;
import com.alibaba.nacos.naming.misc.GlobalConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class DistroConsistencyServiceImplTest extends BaseTest {
    
    private DistroConsistencyServiceImpl distroConsistencyService;
    
    @Mock
    private DataStore dataStore;
    
    @Mock
    private Serializer serializer;
    
    @Mock
    private DistroProtocol distroProtocol;
    
    @Mock
    private ServerMemberManager serverMemberManager;
    
    @Spy
    private GlobalConfig globalConfig;
    
    @Mock
    private DistroConsistencyServiceImpl.Notifier notifier;
    
    @Mock
    private RecordListener<Instances> recordListener;
    
    @Mock
    private DistroComponentHolder distroComponentHolder;
    
    @Mock
    private DistroTaskEngineHolder distroTaskEngineHolder;
    
    private Map<String, ConcurrentLinkedQueue<RecordListener>> listeners;
    
    private Instances instances;
    
    @Before
    public void setUp() throws Exception {
        doReturn(distroComponentHolder).when(context).getBean(DistroComponentHolder.class);
        doReturn(distroTaskEngineHolder).when(context).getBean(DistroTaskEngineHolder.class);
        distroConsistencyService = new DistroConsistencyServiceImpl(distroMapper, dataStore, serializer, switchDomain,
                globalConfig, distroProtocol);
        ReflectionTestUtils.setField(distroConsistencyService, "notifier", notifier);
        ReflectionTestUtils.setField(distroConsistencyService, "distroProtocol", distroProtocol);
        listeners = (Map<String, ConcurrentLinkedQueue<RecordListener>>) ReflectionTestUtils
                .getField(distroConsistencyService, "listeners");
        instances = new Instances();
        
    }
    
    @After
    public void tearDown() throws Exception {
    }
    
    @Test
    public void testPutWithListener() throws NacosException {
        String key = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        distroConsistencyService.listen(key, recordListener);
        distroConsistencyService.put(key, instances);
        verify(distroProtocol).sync(new DistroKey(key, KeyBuilder.INSTANCE_LIST_KEY_PREFIX), ApplyAction.CHANGE, 1000L);
        verify(notifier).addTask(key, ApplyAction.CHANGE);
        verify(dataStore).put(eq(key), any(Datum.class));
    }
    
    @Test
    public void testPutWithoutListener() throws NacosException {
        String key = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        distroConsistencyService.put(key, instances);
        verify(distroProtocol).sync(new DistroKey(key, KeyBuilder.INSTANCE_LIST_KEY_PREFIX), ApplyAction.CHANGE, 1000L);
        verify(notifier, never()).addTask(key, ApplyAction.CHANGE);
        verify(dataStore).put(eq(key), any(Datum.class));
    }
    
    @Test
    public void testRemoveWithListener() throws NacosException {
        String key = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        distroConsistencyService.listen(key, recordListener);
        distroConsistencyService.remove(key);
        verify(dataStore).remove(key);
        verify(notifier).addTask(key, ApplyAction.DELETE);
        assertTrue(listeners.isEmpty());
    }
    
    @Test
    public void testRemoveWithoutListener() throws NacosException {
        String key = KeyBuilder.buildInstanceListKey(TEST_NAMESPACE, TEST_SERVICE_NAME, true);
        distroConsistencyService.remove(key);
        verify(dataStore).remove(key);
        verify(notifier, never()).addTask(key, ApplyAction.DELETE);
        assertTrue(listeners.isEmpty());
    }
}
