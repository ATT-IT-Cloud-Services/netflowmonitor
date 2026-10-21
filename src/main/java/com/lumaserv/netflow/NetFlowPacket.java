package com.lumaserv.netflow;

import com.lumaserv.netflow.flowset.FlowData;
import com.lumaserv.netflow.flowset.DataTemplate;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Getter
@FieldDefaults(makeFinal = true)
public class NetFlowPacket {

    private static final int HEADER_LENGTH = 20;
    private static final int FLOWSET_HEADER_LENGTH = 4;

    int version;
    long sysUptime;
    long timestamp;
    int flowSequence;
    int sourceId;
    List<DataTemplate> dataTemplates = new ArrayList<>();
    List<FlowData> data = new ArrayList<>();

    public NetFlowPacket(byte[] packet) {
        if (packet == null || packet.length < HEADER_LENGTH) {
            throw new IllegalArgumentException("Packet too short for NetFlow v9 header");
        }

        version = ((packet[0] & 0xFF) << 8) | (packet[1] & 0xFF);
        int count = ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);
        sysUptime = ((packet[4] & 0xFFL) << 24) |
                ((packet[5] & 0xFFL) << 16) |
                ((packet[6] & 0xFFL) << 8) |
                (packet[7] & 0xFFL);
        timestamp = ((packet[8] & 0xFFL) << 24) |
                ((packet[9] & 0xFFL) << 16) |
                ((packet[10] & 0xFFL) << 8) |
                (packet[11] & 0xFFL);
        flowSequence = ((packet[12] & 0xFF) << 24) |
                ((packet[13] & 0xFF) << 16) |
                ((packet[14] & 0xFF) << 8) |
                (packet[15] & 0xFF);
        sourceId = ((packet[16] & 0xFF) << 24) |
                ((packet[17] & 0xFF) << 16) |
                ((packet[18] & 0xFF) << 8) |
                (packet[19] & 0xFF);
        int offset = HEADER_LENGTH;
        for (int i = 0; i < count && offset + FLOWSET_HEADER_LENGTH <= packet.length; i++) {
            int id = ((packet[offset] & 0xFF) << 8) | (packet[offset + 1] & 0xFF);
            int length = ((packet[offset + 2] & 0xFF) << 8) | (packet[offset + 3] & 0xFF);

            if (length < FLOWSET_HEADER_LENGTH || offset + length > packet.length) {
                break;
            }

            int payloadLength = length - FLOWSET_HEADER_LENGTH;
            byte[] data = new byte[payloadLength];
            System.arraycopy(packet, offset + FLOWSET_HEADER_LENGTH, data, 0, payloadLength);

            if (id == 0) {
                dataTemplates.add(new DataTemplate(data));
            } else {
                this.data.add(new FlowData(id, data));
            }

            offset += length;
        }
    }

}
