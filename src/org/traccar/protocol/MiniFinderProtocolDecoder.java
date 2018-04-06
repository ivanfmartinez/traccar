/*
 * Copyright 2014 - 2017 Anton Tananaev (anton@traccar.org)
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
package org.traccar.protocol;

import org.jboss.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.DeviceSession;
import org.traccar.helper.BitUtil;
import org.traccar.helper.Log;
import org.traccar.helper.Parser;
import org.traccar.helper.PatternBuilder;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Position;

import java.net.SocketAddress;
import java.util.regex.Pattern;

public class MiniFinderProtocolDecoder extends BaseProtocolDecoder {

    public MiniFinderProtocolDecoder(MiniFinderProtocol protocol) {
        super(protocol);
    }

    private static final Pattern PATTERN_FIX = new PatternBuilder()
            .number("(d+)/(d+)/(d+),")           // date (dd/mm/yy)
            .number("(d+):(d+):(d+),")           // time (hh:mm:ss)
            .number("(-?d+.d+),")                // latitude
            .number("(-?d+.d+),")                // longitude
            .compile();

    private static final Pattern PATTERN_STATE = new PatternBuilder()
            .number("(d+.?d*),")                 // speed (km/h)
            .number("(d+.?d*),")                 // course
            .number("(x+),")                     // flags
            .number("(-?d+.d+),")                // altitude (meters)
            .number("(d+),")                     // battery (percentage)
            .compile();

    private static final Pattern PATTERN_A = new PatternBuilder()
            .text("!A,")
            .expression(PATTERN_FIX.pattern())
            .any()                               // unknown 3 fields
            .compile();

   private static final Pattern PATTERN_C = new PatternBuilder()
            .text("!C,")
            .expression(PATTERN_FIX.pattern())
            .expression(PATTERN_STATE.pattern())
            .any()                               // unknown 3 fields
            .compile();

   // !3,ok/error; result for last set parameter
   private static final Pattern PATTERN_3 = new PatternBuilder()
            .text("!3,")
            .text("(ok|error)")                 // response
            .compile();

   // !5,csq,sta; CSQ 0-31 - sta A-has GPS signal, V no GPS signal
   private static final Pattern PATTERN_5 = new PatternBuilder()
            .text("!5,")
            .number("(d+),")                    // CSQ
            .text("([^;]+)")                    // STA
            .compile();

   // !7,version,csq;  Firmware version
   private static final Pattern PATTERN_7 = new PatternBuilder()
            .text("!7,")
            .text("([^,]+)")                    // version
            .number("(d+)")                     // CSQ
            .compile();

    private static final Pattern PATTERN_BD = new PatternBuilder()
            .expression("![BD],")                // B - buffered, D - live
            .expression(PATTERN_FIX.pattern())
            .expression(PATTERN_STATE.pattern())
            .number("(d+),")                     // satellites in use
            .number("(d+),")                     // satellites in view
            .number("(d+.?d*)")                  // hdop
            .compile();

    private void decodeFix(Position position, Parser parser) {

        position.setTime(parser.nextDateTime(Parser.DateTimeFormat.DMY_HMS));
        position.setLatitude(parser.nextDouble(0));
        position.setLongitude(parser.nextDouble(0));
    }

    private void decodeFlags(Position position, int flags) {

        position.setValid(BitUtil.to(flags, 2) > 0);
        if (BitUtil.check(flags, 1)) {
            position.set(Position.KEY_APPROXIMATE, true);
        }

        if (BitUtil.check(flags, 2)) {
            position.set(Position.KEY_ALARM, Position.ALARM_FAULT);
        }
        if (BitUtil.check(flags, 6)) {
            position.set(Position.KEY_ALARM, Position.ALARM_SOS);
        }
        if (BitUtil.check(flags, 7)) {
            position.set(Position.KEY_ALARM, Position.ALARM_OVERSPEED);
        }
        if (BitUtil.check(flags, 8)) {
            position.set(Position.KEY_ALARM, Position.ALARM_FALL_DOWN);
        }
        if (BitUtil.check(flags, 9) || BitUtil.check(flags, 10) || BitUtil.check(flags, 11)) {
            position.set(Position.KEY_ALARM, Position.ALARM_GEOFENCE);
        }
        if (BitUtil.check(flags, 12)) {
            position.set(Position.KEY_ALARM, Position.ALARM_LOW_BATTERY);
        }
        if (BitUtil.check(flags, 15) || BitUtil.check(flags, 14)) {
            position.set(Position.KEY_ALARM, Position.ALARM_MOVEMENT);
        }

        position.set(Position.KEY_RSSI, BitUtil.between(flags, 16, 21));
        position.set(Position.KEY_CHARGE, BitUtil.check(flags, 22));
    }

    private void decodeState(Position position, Parser parser) {

        position.setSpeed(UnitsConverter.knotsFromKph(parser.nextDouble(0)));

        position.setCourse(parser.nextDouble(0));
        if (position.getCourse() > 360) {
            position.setCourse(0);
        }

        decodeFlags(position, parser.nextHexInt(0));

        position.setAltitude(parser.nextDouble(0));

        position.set(Position.KEY_BATTERY_LEVEL, parser.nextInt(0));
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        String sentence = (String) msg;

        if (sentence.startsWith("!1,")) {
            int index = sentence.indexOf(',', 3);
            if (index < 0) {
                index = sentence.length();
            }
            getDeviceSession(channel, remoteAddress, sentence.substring(3, index));
            return null;
        }

        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress);
        if (deviceSession == null || !sentence.matches("![A-D3457],.*")) {
            return null;
        }

        Position position = new Position(getProtocolName());
        position.setDeviceId(deviceSession.getDeviceId());

        String type = sentence.substring(1, 2);
        position.set(Position.KEY_TYPE, type);

        if (type.equals("B") || type.equals("D")) {

            Parser parser = new Parser(PATTERN_BD, sentence);
            if (!parser.matches()) {
                Log.error("Invalid sentence : " + sentence);
                return null;
            }

            decodeFix(position, parser);
            decodeState(position, parser);

            position.set(Position.KEY_SATELLITES, parser.nextInt(0));
            position.set(Position.KEY_SATELLITES_VISIBLE, parser.nextInt(0));
            position.set(Position.KEY_HDOP, parser.nextDouble(0));

            return position;

        } else if (type.equals("C")) {

            Parser parser = new Parser(PATTERN_C, sentence);
            if (!parser.matches()) {
                Log.error("Invalid sentence : " + sentence);
                return null;
            }

            decodeFix(position, parser);
            decodeState(position, parser);

            return position;

        } else if (type.equals("A")) {

            Parser parser = new Parser(PATTERN_A, sentence);
            if (!parser.matches()) {
                Log.error("Invalid sentence : " + sentence);
                return null;
            }

            decodeFix(position, parser);

            return position;

        } else if (type.equals("3")) {
            Parser parser = new Parser(PATTERN_3, sentence);
            if (!parser.matches()) {
                Log.error("Invalid sentence : " + sentence);
                return null;
            }
            position.set(Position.KEY_STATUS, parser.next());

            return position;

        } else if (type.equals("4")) {
            // !4,f1,f2,f3,f4,f5,f6,f7,f8,f9; Check Status

            Log.error("Unsupported sentence : " + sentence);

            return null;
        } else if (type.equals("5")) {
            Parser parser = new Parser(PATTERN_5, sentence);
            if (!parser.matches()) {
                Log.error("Invalid sentence : " + sentence);
                return null;
            }
            position.set(Position.KEY_RSSI, parser.nextInt(0));
            position.set(Position.KEY_GPS, parser.next());

            return position;
        } else if (type.equals("7")) {
            Parser parser = new Parser(PATTERN_7, sentence);
            if (!parser.matches()) {
                Log.error("Invalid sentence : " + sentence);
                return null;
            }
            position.set(Position.KEY_STATUS, parser.next()); // Version
            position.set(Position.KEY_RSSI, parser.nextInt(0));

            return position;
        }

        Log.error("Invalid sentence : " + sentence);
        return null;
    }

}
