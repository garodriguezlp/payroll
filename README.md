# Payroll CLI Tool

## What is Payroll?

Payroll is a CLI tool built with Java and Jbang, designed to process PDF files following Perficient Colombia's payroll standards.
It extracts information from bi-weekly payment vouchers and converts it into a CSV-like format for easy analysis in spreadsheet
applications. This tool is tailored to streamline data extraction from payroll documents.

## How to Use Payroll

To use the Payroll CLI tool, follow these simple steps:

1. Navigate to the folder containing all the relevant PDF files. Ensure that this folder only contains the PDFs you wish to
process.

2. Execute the following command:

```bash
payroll * | pbcopy
```

This command processes all PDF files in the current directory, extracting the necessary data and copying it to your clipboard in a
CSV-like format, ready to be pasted into your preferred spreadsheet application for analysis.

## Installation

To install the Payroll CLI tool, first install [Jbang](https://jbang.dev/) if not already done. Then, use the following command to
install the Payroll tool:

```bash
jbang app install payroll@garodriguezlp
```

This command installs the Payroll tool, making it readily available for use on your system.
