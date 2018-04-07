/*
 * Copyright 2016 - 2018 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar;

import java.util.TimeZone;
import java.util.Map;
import java.util.HashMap;

public class DeviceSession {

    private final long deviceId;
    private Map<String, Object> attributes;

    public DeviceSession(long deviceId) {
        this.deviceId = deviceId;
    }

    public long getDeviceId() {
        return deviceId;
    }

    private TimeZone timeZone;

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public TimeZone getTimeZone() {
        return timeZone;
    }

    public Map<String, Object> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        return attributes;
    }

    public void set(String key, Object value) {
        getAttributes().put(key, value);
    }

    public Object get(String key, Object defaultValue) {
        return getAttributes().getOrDefault(key, defaultValue);
    }

}
