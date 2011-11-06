/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality.internal;

import org.codenarc.AnalysisContext;
import org.codenarc.report.ReportWriter;
import org.codenarc.results.Results;
import org.codenarc.rule.Violation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Formatter;

public class ConsoleReportWriter implements ReportWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleReportWriter.class);

    public void writeReport(AnalysisContext analysisContext, Results results) {
        report(results);
    }

    private void report(Results results) {
        if (!results.getChildren().isEmpty()) {
            for (Object child : results.getChildren()) {
                report((Results) child);
            }
            return;
        }

        for (Object o : results.getViolations()) {
            Violation v = (Violation) o;
            Formatter formatter = new Formatter();
            formatter.format("%s:%s%n", results.getPath(), v.getLineNumber());
            formatter.format("%s: %s", v.getRule().getName(), v.getMessage());
            LOGGER.error(formatter.toString());
        }
    }

}
