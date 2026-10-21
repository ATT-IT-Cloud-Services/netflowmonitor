package com.lumaserv.netflow.flowset;

import java.util.HashMap;
import java.util.Map;

public record FlowData(int id, byte[] data) {

    public Map<FlowField, FlowValue> parse(DataTemplate template) {
        Map<FlowField, FlowValue> values = new HashMap<>();
        int offset = 0;
        for (TemplateField field : template.getFields()) {
            byte[] value = new byte[field.length()];
            System.arraycopy(data, offset, value, 0, value.length);
            values.put(field.type(), new FlowValue(value));
            offset += value.length;
        }
        return values;
    }

}
