///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS org.apache.pdfbox:pdfbox:2.0.26
//DEPS org.apache.commons:commons-csv:1.9.0
//DEPS org.projectlombok:lombok:1.18.24

import lombok.Builder;
import lombok.Value;
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

import static org.apache.commons.csv.CSVFormat.TDF;

@Command(name = "payroll", mixinStandardHelpOptions = true, version = "payroll 0.1",
        description = "payroll made with jbang")
class payroll implements Callable<Integer> {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getNumberInstance(Locale.US);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final int SECOND_MATCHING_GROUP = 2;
    private static final int FIRST_MATCHING_GROUP = 1;

    private static final Pattern DATE_PATTERN = Pattern.compile("Fecha cpbte: ((\\d{2}/){2}(\\d{4}))");
    private static final String NUMBER_REGEX = "((\\d{1,3},){1,3}(\\d*).(\\d*))";
    private static final Pattern WORK_HOURS_PATTERN = Pattern.compile("laboradas: {2}" + "((\\d{1,3}).(\\d*))");
    private static final Pattern WAGE_PATTERN = Pattern.compile("Salario: {2}" + NUMBER_REGEX);

    private static final Pattern PERIOD_WAGE_PATTERN = Pattern.compile("(SALARIO BASICO|S.I. INTEGRAL) \\+ {2}" + NUMBER_REGEX);
    private static final Pattern VACATIONS_PATTERN = Pattern.compile("VACACIONES \\+ {2}" + NUMBER_REGEX);
    private static final Pattern TOTAL_WAGE_PATTERN = Pattern.compile("TOTAL DEVENGADOS {2}" + NUMBER_REGEX);

    private static final Pattern RETENTION_PATTERN = Pattern.compile("RETENCION EN LA FUENTE - {2}" + NUMBER_REGEX);
    private static final Pattern HEALTH_PATTERN = Pattern.compile("DEDUCCION SALUD - {2}" + NUMBER_REGEX);
    private static final Pattern PENSION_PATTERN = Pattern.compile("DEDUCCION PENSION - {2}" + NUMBER_REGEX);
    private static final Pattern SOLIDARITY_PATTERN = Pattern.compile("FONDO DE SOLIDARIDAD - {2}" + NUMBER_REGEX);
    private static final Pattern AFC_PATTERN = Pattern.compile("AFC BANCOLOMBIA - {2}" + NUMBER_REGEX);
    private static final Pattern TOTAL_DEDUCTIONS_PATTERN = Pattern.compile("TOTAL DEDUCCIONES {2}" + NUMBER_REGEX);

    private static final Pattern TOTAL_PAYMENT_PATTERN = Pattern.compile("TOTAL A PAGAR: {2}" + NUMBER_REGEX);

    private static final CSVFormat CSV_FORMAT = CSVFormat.Builder
            .create(TDF)
            .setHeader(PayrollInfo.getHeaders())
            .build();

    @Parameters(paramLabel = "PDF_FILE", description = "one or more pdf files to analyze")
    File[] files;

    private PDFTextStripper pdfTextStripper;

    private List<PayrollInfo> payrollInfoList;

    @Value
    @Builder
    static class PayrollInfo {
        LocalDate date;
        Number workHours;
        Number wage;

        Number periodWage;
        Number vacations;
        Number totalWage;

        Number retention;
        Number health;
        Number pension;
        Number solidarity;
        Number afc;
        Number totalDeductions;

        Number totalPayment;

        public Object[] getValues() {
            return new Object[]{
                    date,
                    workHours,
                    wage,
                    periodWage,
                    vacations,
                    totalWage,
                    retention,
                    health,
                    pension,
                    solidarity,
                    afc,
                    totalDeductions,
                    totalPayment
            };
        }

        public static String[] getHeaders() {
            return new String[]{
                    "date",
                    "workHours",
                    "wage",
                    "periodWage",
                    "vacations",
                    "totalWage",
                    "retention",
                    "health",
                    "pension",
                    "solidarity",
                    "afc",
                    "totalDeductions",
                    "totalPayment"
            };
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
            try (PDDocument pdfDocument = PDDocument.load(file)) {
                String strippedText = pdfTextStripper.getText(pdfDocument);
                payrollInfoList.add(PayrollInfo.builder()
                        .date(extractValue(strippedText, DATE_PATTERN, v -> LocalDate.parse(v, DATE_TIME_FORMATTER), FIRST_MATCHING_GROUP))
                        .workHours(extractValue(strippedText, WORK_HOURS_PATTERN, NUMBER_FORMAT::parse, FIRST_MATCHING_GROUP))
                        .wage(extractValue(strippedText, WAGE_PATTERN, NUMBER_FORMAT::parse, FIRST_MATCHING_GROUP))
                        .periodWage(extractValue(strippedText, PERIOD_WAGE_PATTERN, NUMBER_FORMAT::parse, SECOND_MATCHING_GROUP))
                        .vacations(extractValue(strippedText, VACATIONS_PATTERN, NUMBER_FORMAT::parse, FIRST_MATCHING_GROUP))
                        .totalWage(extractValue(strippedText, TOTAL_WAGE_PATTERN, NUMBER_FORMAT::parse, FIRST_MATCHING_GROUP))
                        .retention(extractValue(strippedText, RETENTION_PATTERN, NUMBER_FORMAT::parse, FIRST_MATCHING_GROUP))
                        .health(extractValue(strippedText, HEALTH_PATTERN, NUMBER_FORMAT::parse, FIRST_MATCHING_GROUP))
                        .pension(extractValue(strippedText, PENSION_PATTERN, NUMBER_FORMAT::parse, FIRST_MATCHING_GROUP))
                        .solidarity(extractValue(strippedText, SOLIDARITY_PATTERN, NUMBER_FORMAT::parse, FIRST_MATCHING_GROUP))
                        .afc(extractValue(strippedText, AFC_PATTERN, NUMBER_FORMAT::parse, FIRST_MATCHING_GROUP))
                        .totalDeductions(extractValue(strippedText, TOTAL_DEDUCTIONS_PATTERN, NUMBER_FORMAT::parse, FIRST_MATCHING_GROUP))
                        .totalPayment(extractValue(strippedText, TOTAL_PAYMENT_PATTERN, NUMBER_FORMAT::parse, FIRST_MATCHING_GROUP))
                        .build());
            }
        }

        printAsCSV();
        return 0;
    }

    private void initPdfTextStripper() throws IOException {
        pdfTextStripper = new PDFTextStripper();
        pdfTextStripper.setSortByPosition(true);
    }

    private static <T> T extractValue(String text, Pattern retentionPattern, ValueParser<T> parser, int group) throws Exception {
        Matcher matcher = retentionPattern.matcher(text);
        if (matcher.find()) {
            return parser.parse(matcher.group(group));
        }
        return null;
    }

    private void printAsCSV() throws IOException {
        try (CSVPrinter printer = new CSVPrinter(System.out, CSV_FORMAT)) {
            for (PayrollInfo payrollInfo : payrollInfoList) {
                printer.printRecord(payrollInfo.getValues());
            }
        }
    }
}
