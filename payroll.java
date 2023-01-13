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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.text.NumberFormat.getNumberInstance;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Locale.US;
import static java.util.regex.Pattern.compile;
import static org.apache.commons.csv.CSVFormat.TDF;

@Command(name = "payroll", mixinStandardHelpOptions = true, version = "payroll 0.1",
        description = "payroll made with jbang")
class payroll implements Callable<Integer> {

    private static final NumberFormat NUMBER_FORMAT = getNumberInstance(US);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = ofPattern("dd/MM/yyyy");

    private static final Pattern DATE_PATTERN = compile("Fecha cpbte: ((\\d{2}/){2}(\\d{4}))");
    private static final String NUMBER_REGEX = "((\\d{1,3},){1,3}(\\d*).(\\d*))";
    private static final Pattern WORK_HOURS_PATTERN = compile("laboradas: {2}" + "((\\d{1,3}).(\\d*))");
    private static final Pattern WAGE_PATTERN = compile("Salario: {2}" + NUMBER_REGEX);

    private static final Pattern PERIOD_WAGE_PATTERN = compile("(SALARIO BASICO|S.I. INTEGRAL) \\+ {2}" + NUMBER_REGEX);
    private static final Pattern VACATIONS_PATTERN = compile("VACACIONES \\+ {2}" + NUMBER_REGEX);
    private static final Pattern COLSANITAS_IN_PATTERN = compile("MEDIC PREP COLSANITAS \\+ {2}" + NUMBER_REGEX);
    private static final Pattern CALAMITY_PATTERN = compile("CALAMIDAD DOMESTICA \\+ {2}" + NUMBER_REGEX);
    private static final Pattern VOTE_PATTERN = compile("DIA LIBRE POR VOTACION \\+ {2}" + NUMBER_REGEX);
    private static final Pattern DISABILITY_PATTERN = compile("INCAPACIDAD POR \\+ {2}" + NUMBER_REGEX);
    private static final Pattern FREE_DAT_INTEGRAL_SALARY_PATTERN = compile("DIA LIBRE SALARIO INTEGRAL \\+ {2}" + NUMBER_REGEX);
    private static final Pattern TOTAL_WAGE_PATTERN = compile("TOTAL DEVENGADOS {2}" + NUMBER_REGEX);

    private static final Pattern RETENTION_PATTERN = compile("RETENCION EN LA FUENTE - {2}" + NUMBER_REGEX);
    private static final Pattern HEALTH_PATTERN = compile("DEDUCCION SALUD - {2}" + NUMBER_REGEX);
    private static final Pattern PENSION_PATTERN = compile("DEDUCCION PENSION - {2}" + NUMBER_REGEX);
    private static final Pattern SOLIDARITY_PATTERN = compile("FONDO DE SOLIDARIDAD - {2}" + NUMBER_REGEX);
    private static final Pattern AFC_PATTERN = compile("AFC BANCOLOMBIA - {2}" + NUMBER_REGEX);
    private static final Pattern COLSANITAS_OUT_PATTERN = compile("MEDIC PREP COLSANITAS - {2}" + NUMBER_REGEX);
    private static final Pattern DAVIVIENDA_PATTERN = compile("DED CRED DAVIVIENDA - {2}" + NUMBER_REGEX);
    private static final Pattern TOTAL_DEDUCTIONS_PATTERN = compile("TOTAL DEDUCCIONES {2}" + NUMBER_REGEX);

    private static final Pattern TOTAL_PAYMENT_PATTERN = compile("TOTAL A PAGAR: {2}" + NUMBER_REGEX);

    private static final CSVFormat CSV_FORMAT = CSVFormat.Builder
            .create(TDF)
            .setHeader(PayrollInfo.getHeaders())
            .build();

    @Parameters(paramLabel = "PDF_FILE", description = "one or more pdf files to analyze", arity = "1..*")
    File[] files;


    public static void main(String... args) {
        int exitCode = new CommandLine(new payroll()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        PDFTextStripper pdfTextStripper = buildPDFTextStripper();
        List<PayrollInfo> payrollInfoList = new ArrayList<>();

        for (File file : files) {
            try (PDDocument pdfDocument = PDDocument.load(file)) {
                String strippedText = pdfTextStripper.getText(pdfDocument);
                payrollInfoList.add(PayrollInfo.builder()
                        .date(extractValue(strippedText, DATE_PATTERN, 1, v -> LocalDate.parse(v, DATE_TIME_FORMATTER)))
                        .workHours(extractValue(strippedText, WORK_HOURS_PATTERN, 1, NUMBER_FORMAT::parse))
                        .wage(extractValue(strippedText, WAGE_PATTERN, 1, NUMBER_FORMAT::parse))
                        .periodWage(extractValue(strippedText, PERIOD_WAGE_PATTERN, 2, NUMBER_FORMAT::parse))
                        .vacations(extractValue(strippedText, VACATIONS_PATTERN, 1, NUMBER_FORMAT::parse))
                        .colsanitasIn(extractValue(strippedText, COLSANITAS_IN_PATTERN, 1, NUMBER_FORMAT::parse))
                        .calamity(extractValue(strippedText, CALAMITY_PATTERN, 1, NUMBER_FORMAT::parse))
                        .vote(extractValue(strippedText, VOTE_PATTERN, 1, NUMBER_FORMAT::parse))
                        .disability(extractValue(strippedText, DISABILITY_PATTERN, 1, NUMBER_FORMAT::parse))
                        .freeDayIntegralSalary(extractValue(strippedText, FREE_DAT_INTEGRAL_SALARY_PATTERN, 1, NUMBER_FORMAT::parse))
                        .totalWage(extractValue(strippedText, TOTAL_WAGE_PATTERN, 1, NUMBER_FORMAT::parse))
                        .retention(extractValue(strippedText, RETENTION_PATTERN, 1, NUMBER_FORMAT::parse))
                        .health(extractValue(strippedText, HEALTH_PATTERN, 1, NUMBER_FORMAT::parse))
                        .pension(extractValue(strippedText, PENSION_PATTERN, 1, NUMBER_FORMAT::parse))
                        .solidarity(extractValue(strippedText, SOLIDARITY_PATTERN, 1, NUMBER_FORMAT::parse))
                        .afc(extractValue(strippedText, AFC_PATTERN, 1, NUMBER_FORMAT::parse))
                        .colsanitasOut(extractValue(strippedText, COLSANITAS_OUT_PATTERN, 1, NUMBER_FORMAT::parse))
                        .davivienda(extractValue(strippedText, DAVIVIENDA_PATTERN, 1, NUMBER_FORMAT::parse))
                        .totalDeductions(extractValue(strippedText, TOTAL_DEDUCTIONS_PATTERN, 1, NUMBER_FORMAT::parse))
                        .totalPayment(extractValue(strippedText, TOTAL_PAYMENT_PATTERN, 1, NUMBER_FORMAT::parse))
                        .build());
            }
        }
        printAsCSV(sortByDate(payrollInfoList));
        return 0;
    }

    private List<PayrollInfo> sortByDate(List<PayrollInfo> payrollInfoList) {
        return payrollInfoList.stream()
            .sorted(Comparator.comparing(PayrollInfo::getDate))
            .collect(Collectors.toList());
    }

    private PDFTextStripper buildPDFTextStripper() throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper;
    }

    private <T> T extractValue(String text, Pattern pattern, int matcherGroup, ValueParser<T> parser) throws Exception {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return parser.parse(matcher.group(matcherGroup));
        }
        return null;
    }

    private void printAsCSV(List<PayrollInfo> payrollInfoList) throws IOException {
        try (CSVPrinter printer = new CSVPrinter(System.out, CSV_FORMAT)) {
            for (PayrollInfo payrollInfo : payrollInfoList) {
                printer.printRecord(payrollInfo.getValues());
            }
        }
    }

    @FunctionalInterface
    interface ValueParser<T> {

        T parse(String value) throws Exception;
    }

    @Value
    static class PayrollInfo {
        LocalDate date;
        Number workHours;
        Number wage;

        Number periodWage;
        Number vacations;
        Number colsanitasIn;
        Number calamity;
        Number totalWage;
        Number vote;
        Number disability;
        Number freeDayIntegralSalary;
        Number missingEarnings;

        Number retention;
        Number health;
        Number pension;
        Number solidarity;
        Number afc;
        Number colsanitasOut;
        Number davivienda;
        Number totalDeductions;
        Number missingDeductions;

        Number totalPayment;

        @Builder
        private PayrollInfo(LocalDate date,
                            Number workHours,
                            Number wage,
                            Number periodWage,
                            Number vacations,
                            Number colsanitasIn,
                            Number calamity,
                            Number vote,
                            Number disability,
                            Number freeDayIntegralSalary,
                            Number totalWage,
                            Number retention,
                            Number health,
                            Number pension,
                            Number solidarity,
                            Number afc,
                            Number colsanitasOut,
                            Number davivienda,
                            Number totalDeductions,
                            Number totalPayment) {
            this.date = date;
            this.workHours = workHours;
            this.wage = wage;
            this.periodWage = periodWage;
            this.vacations = vacations;
            this.colsanitasIn = colsanitasIn;
            this.calamity = calamity;
            this.vote = vote;
            this.disability = disability;
            this.freeDayIntegralSalary = freeDayIntegralSalary;
            this.totalWage = totalWage;
            this.missingEarnings = calculateMissingEarnings();
            this.retention = retention;
            this.health = health;
            this.pension = pension;
            this.solidarity = solidarity;
            this.afc = afc;
            this.colsanitasOut = colsanitasOut;
            this.davivienda = davivienda;
            this.totalDeductions = totalDeductions;
            this.missingDeductions = calculateMissingDeductions();
            this.totalPayment = totalPayment;
        }

        private Number calculateMissingEarnings() {
            return calculateDifferences(totalWage,
                periodWage,
                vacations,
                colsanitasIn,
                calamity,
                vote,
                disability,
                freeDayIntegralSalary);
        }

        private Number calculateMissingDeductions() {
            return calculateDifferences(totalDeductions,
                retention,
                health,
                pension,
                solidarity,
                afc,
                colsanitasOut,
                davivienda);
        }

        private Number calculateDifferences(Number expected, Number... items){
            Double sumOfItems = Stream.of(items)
                .filter(Objects::nonNull)
                .map(Number::doubleValue)
                .reduce(Double::sum)
                .orElse(0.0);

            return Optional.ofNullable(expected)
                .map(Number::doubleValue)
                .map(e -> e - sumOfItems)
                .orElse(sumOfItems);
        }

        public static String[] getHeaders() {
            return new String[]{
                "Fecha",
                "Horas laboradas",
                "Salario",
                "Salario periodo",
                "Vacaciones",
                "Colsanitas ingreso",
                "Calamidad",
                "Voto",
                "Discapacidad",
                "Salario integral dia festivo",
                "Ingresos faltantes",
                "Total ingresos",
                "Retencion",
                "Salud",
                "Pension",
                "Solidaridad",
                "AFC",
                "Colsanitas egreso",
                "Davivienda",
                "Total deducciones",
                "Deducciones faltantes",
                "Total pago"
            };
        }

        public Object[] getValues() {
            return new Object[]{
                date,
                workHours,
                wage,
                periodWage,
                vacations,
                colsanitasIn,
                calamity,
                vote,
                disability,
                freeDayIntegralSalary,
                missingEarnings,
                totalWage,
                retention,
                health,
                pension,
                solidarity,
                afc,
                colsanitasOut,
                davivienda,
                totalDeductions,
                missingDeductions,
                totalPayment
            };
        }
    }
}
