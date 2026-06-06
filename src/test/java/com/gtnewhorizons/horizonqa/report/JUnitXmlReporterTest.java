package com.gtnewhorizons.horizonqa.report;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class JUnitXmlReporterTest {

    @Test
    public void xmlAttributesEscapeEntitiesAndNormalizeWhitespace() {
        String escaped = JUnitXmlReporter.sanitizeAttr("\"quoted\" & <tag> >\r\n\t ok\u0001\u007F\u0085");

        assertEquals("&quot;quoted&quot; &amp; &lt;tag&gt; &gt;    ok", escaped);
    }

    @Test
    public void xmlBodiesEscapeEntitiesAndPreserveLegalWhitespace() {
        String escaped = JUnitXmlReporter.escapeBody("body & <tag> >\n\t\r ok\u0000\u007F\u0085");

        assertEquals("body &amp; &lt;tag&gt; &gt;\n\t\r ok", escaped);
    }
}
