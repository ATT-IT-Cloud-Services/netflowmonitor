package gg.zema.elizon.netflowmonitor.utilities;

import com.lumaserv.netflow.flowset.FlowField;
import com.lumaserv.netflow.flowset.FlowValue;

import java.util.Map;

public final class FlowDecoder {

    public IpFlowMetricsRepository.Row decode(Map<FlowField, FlowValue> values) {
        Long fromIpv4Raw = asUInt(values.get(FlowField.IPV4_SRC_ADDR));
        byte[] fromIpv6Raw = asIpv6Bytes(values.get(FlowField.IPV6_SRC_ADDR));
        Long toIpv4Raw = asUInt(values.get(FlowField.IPV4_DST_ADDR));
        byte[] toIpv6Raw = asIpv6Bytes(values.get(FlowField.IPV6_DST_ADDR));
        Long nextHopIpv4Raw = asUInt(values.get(FlowField.IPV4_NEXT_HOP));
        byte[] nextHopIpv6Raw = asIpv6Bytes(values.get(FlowField.IPV6_NEXT_HOP));

        int protocolId = asUByte(values.get(FlowField.PROTOCOL));
        Integer srcPort = asUShort(values.get(FlowField.L4_SRC_PORT));
        Integer dstPort = asUShort(values.get(FlowField.L4_DST_PORT));

        return new IpFlowMetricsRepository.Row(
                fromIpv4Raw,
                fromIpv6Raw,
                toIpv4Raw,
                toIpv6Raw,
                asUInt(values.get(FlowField.IN_BYTES)),
                nextHopIpv4Raw,
                nextHopIpv6Raw,
                protocolId,
                srcPort == null ? 0 : srcPort,
                dstPort == null ? 0 : dstPort,
                clampToInt(asUIntOrZero(values.get(FlowField.IN_PKTS)))
        );
    }

    private byte[] asIpv6Bytes(FlowValue value) {
        if (value == null || value.length() != 16) {
            return null;
        }

        return value.asBytes().clone();
    }

    private int asUByte(FlowValue value) {
        if (value == null || value.length() != 1) {
            return 0;
        }
        return value.asByte() & 0xFF;
    }

    private Integer asUShort(FlowValue value) {
        if (value == null || value.length() != 2) {
            return null;
        }
        return value.asUShort();
    }

    private Long asUInt(FlowValue value) {
        if (value == null || value.length() != 4) {
            return null;
        }
        return value.asUInt();
    }

    private long asUIntOrZero(FlowValue value) {
        Long converted = asUInt(value);
        return converted == null ? 0L : converted;
    }

    private int clampToInt(long value) {
        if (value < 0) {
            return 0;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }
}

