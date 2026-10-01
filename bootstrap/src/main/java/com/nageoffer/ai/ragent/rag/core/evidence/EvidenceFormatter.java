/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.core.evidence;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class EvidenceFormatter {
    public String format(List<EvidenceRecord> evidence) {
        if (evidence == null) return "";
        return evidence.stream().filter(EvidenceRecord::isSelectedForPrompt)
                .map(this::formatOne).collect(Collectors.joining("\n\n"));
    }

    private String formatOne(EvidenceRecord item) {
        StringBuilder tag = new StringBuilder("<evidence id=\"").append(attribute(item.getEvidenceId())).append("\"");
        appendAttribute(tag, "document", item.getDocumentTitle());
        appendAttribute(tag, "kb", item.getKbName());
        appendAttribute(tag, "section", item.getSection());
        return tag.append(">\n").append(item.getContent() == null ? "" : item.getContent())
                .append("\n</evidence>").toString();
    }

    private void appendAttribute(StringBuilder tag, String name, String value) {
        if (value != null && !value.isBlank()) tag.append(' ').append(name).append("=\"").append(attribute(value)).append("\"");
    }

    private String attribute(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}
