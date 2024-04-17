package org.traccar.protocol;

import org.traccar.BaseProtocol;
import org.traccar.PipelineBuilder;
import org.traccar.TrackerServer;
import org.traccar.config.Config;

import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import jakarta.inject.Inject;

public class CarStatsViewerProtocol extends BaseProtocol {

    @Inject
    public CarStatsViewerProtocol(Config config) {
        addServer(new TrackerServer(config, getName(), false) {
            @Override
            protected void addProtocolHandlers(PipelineBuilder pipeline, Config config) {
                pipeline.addLast(new HttpResponseEncoder());
                pipeline.addLast(new HttpRequestDecoder());
                // Must support big requests that came when uploading old data
                // got one request with Content-Length: 212332
                pipeline.addLast(new HttpObjectAggregator(256 * 1024));
                pipeline.addLast(new CarStatsViewerDecoder(CarStatsViewerProtocol.this));
            }
        });
    }
}
