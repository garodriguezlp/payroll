///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS org.apache.pdfbox:pdfbox:2.0.26
//DEPS org.apache.commons:commons-csv:1.9.0

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(name = "payroll", mixinStandardHelpOptions = true, version = "payroll 0.1",
        description = "payroll made with jbang")
class payroll implements Callable<Integer> {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final Pattern DATE_PATTERN = Pattern.compile("Fecha cpbte: ((\\d{2}\\/){2}(\\d{4}))");
    private static final String NUMBER_REGEX = "((\\d*,)*(\\d*).(\\d*))";
    private static final Pattern RETENTION_PATTERN = Pattern.compile("RETENCION EN LA FUENTE - {2}" + NUMBER_REGEX);
    private static final Pattern WAGE_PATTERN = Pattern.compile("Salario: {2}" + NUMBER_REGEX);

    private static final CSVFormat CSV_FORMAT = CSVFormat.Builder.create()
            .setHeader("Date", "Wage", "Retention")
            .build();

    @Parameters(paramLabel = "PDF_FILE", description = "one or more pdf files to analyze")
    File[] files;

    private PDFTextStripper pdfTextStripper;

    private List<PayrollInfo> payrollInfoList;

    static class PayrollInfo {
        private final LocalDate date;
        private final Number wage;
        private final Number retention;

        public PayrollInfo(LocalDate date, Number wage, Number retention) {
            this.date = date;
            this.wage = wage;
            this.retention = retention;
        }

        public LocalDate getDate() {
            return date;
        }

        public Number getWage() {
            return wage;
        }

        public Number getRetention() {
            return retention;
        }
    }

    @FunctionalInterface
    interface ValueParser<T> {
        T parse(String value) throws Exception;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new payroll()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        initPdfTextStripper();
        payrollInfoList = new ArrayList<>();

        for (File file : files) {
            System.out.println("Parsing file = " + file);
            String strippedText = pdfTextStripper.getText(PDDocument.load(file));
            System.out.println("strippedText = " + strippedText);
            Number retention = extractValue(strippedText, RETENTION_PATTERN, NUMBER_FORMAT::parse);
            LocalDate date = extractValue(strippedText, DATE_PATTERN, v -> LocalDate.parse(v, DATE_TIME_FORMATTER));
            Number wage = extractValue(strippedText, WAGE_PATTERN, NUMBER_FORMAT::parse);
            payrollInfoList.add(new PayrollInfo(date, wage, retention));
        }

        printAsCSV();
        return 0;
    }

    private void initPdfTextStripper() throws IOException {
        pdfTextStripper = new PDFTextStripper();
        pdfTextStripper.setSortByPosition(true);
    }

    private static <T> T extractValue(String text, Pattern retentionPattern, ValueParser<T> parser) throws Exception {
        Matcher matcher = retentionPattern.matcher(text);
        if (matcher.find()) {
            return parser.parse(matcher.group(1));
        }
        return null;
    }

    private void printAsCSV() throws IOException {
        try (CSVPrinter printer = new CSVPrinter(System.out, CSV_FORMAT)) {
            for (PayrollInfo payrollInfo : payrollInfoList) {
                printer.printRecord(payrollInfo.getDate(),
                        payrollInfo.getWage(),
                        payrollInfo.getRetention());
            }
        }
    }
}
