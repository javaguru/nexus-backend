package com.jservlet.nexus.test.openapi;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.parser.OpenAPIV3Parser;
/*import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;*/

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Read an OpenApi 3.0.1 file yaml or json  (File or Url) and Export OpenApi data to an Excel file
 * Columns:
 * "Uri","Method","Id","Parameters Name,Type,Example,Required","RegExp","BodyRequest","Required","Media","Type"
 *
 * @author frank@jservlet.com, 06.2024
 */
public class OpenApiReader {

   /* // Output Excel file /src/test/resources
    private static final String FILE_NAME = "src/test/resources/mock-api.xlsx";
    private static final String PATH_CONTEXT = "/nexus-backend";
    private static final String SHEET_NAME = "OpenAPI";

    *//*
     * Main OpenApiReader
     * args[0] src\test\resources\mock-api.json
     * or
     * args[0] http://localhost:8082/nexus-backend/v3/api-docs/mock-api
     *//*
    public static void main(String[] args) throws IOException {
        // Load raw data from file format openapi 3.0.1
        OpenAPI api = new OpenAPIV3Parser().read(args[0]);
        // Get the component
        Components components = api.getComponents();
        if (components != null) {
            components.getSchemas();
        }
        // Scan the package
        for (Map.Entry<String, PathItem> entry : api.getPaths().entrySet()) {
            PathItem item = entry.getValue();
            System.out.println(item.getDescription());
            System.out.println(entry.getKey());
            Operation opeGet = item.getGet();
            readParameters(opeGet);
            Operation opePost = item.getPost();
            readParameters(opePost);

        }
        Map<String, Object[]> data = fillData(api.getPaths().entrySet());
        saveFileExcel(data);

    }
    private static void readParameters(Operation operation) {
        if (operation != null) {
            System.out.println(operation.getSummary());
            System.out.println(operation.getOperationId());
            if (operation.getRequestBody() != null) {
                System.out.println(operation.getRequestBody().get$ref());
            }

            List<Parameter> params = operation.getParameters();
            if (params != null) {
                for (Parameter param : params) {
                    System.out.println(param.getName());
                    System.out.println(param.getDescription());
                    System.out.println(param.getSchema().getType());
                    System.out.println(param.getSchema().getDefault());
                    System.out.println(param.getRequired());
                }
            }
        }
    }


    private static Map<String, Object[]> fillData(Set<Map.Entry<String, PathItem>> entrySet) {
        Map<String, Object[]> data = new TreeMap<>();

        XSSFFont font1 = new XSSFFont();
        font1.setBold(true);
        XSSFRichTextString s0 = new XSSFRichTextString("Uri");
        s0.applyFont(font1);
        XSSFRichTextString s1 = new XSSFRichTextString("Method");
        s1.applyFont(font1);
        XSSFRichTextString s2 = new XSSFRichTextString("Id");
        s2.applyFont(font1);
        XSSFRichTextString s3 = new XSSFRichTextString("Parameters" + sep + "Name,Type,Example,Required");
        s3.applyFont(font1);
        XSSFRichTextString s4 = new XSSFRichTextString("RegExp");
        s4.applyFont(font1);
        XSSFRichTextString s5 = new XSSFRichTextString("BodyRequest");
        s5.applyFont(font1);
        XSSFRichTextString s6 = new XSSFRichTextString("Required");
        s6.applyFont(font1);
        XSSFRichTextString s7 = new XSSFRichTextString("Media");
        s7.applyFont(font1);
        XSSFRichTextString s8 = new XSSFRichTextString("Type");
        s8.applyFont(font1);
        // Headers columns
        data.put("0", new Object[] {s0,s1,s2,s3,s4,s5,s6,s7,s8});

        // Body operations columns
        for (Map.Entry<String, PathItem> entry : entrySet) {
            PathItem item = entry.getValue();
            System.out.println(entry.getKey());
            Operation opeGet = item.getGet();
            if (opeGet != null) saveOperation(data, opeGet, entry.getKey(), "GET");
            Operation opePost = item.getPost();
            if (opePost != null) saveOperation(data, opePost, entry.getKey(), "POST");
            Operation opePut = item.getPut();
            if (opePut != null) saveOperation(data, opePut, entry.getKey(), "PUT");
            Operation opeDel = item.getDelete();
            if (opeDel != null) saveOperation(data, opeDel, entry.getKey(), "DELETE");
            Operation opePatch = item.getPatch();
            if (opePatch != null) saveOperation(data, opePatch, entry.getKey(), "PATCH");
            Operation opeOptions = item.getOptions();
            if (opeOptions != null) saveOperation(data, opeOptions, entry.getKey(), "OPTIONS");
        }
        return data;
    }

    private static int count = 0;
    private static final String sep = "\n";

    private static void saveOperation(Map<String, Object[]> data, Operation operation, String uri, String method) {
        // Body operations columns
        StringBuilder sb = new StringBuilder();
        List<Parameter> params = operation.getParameters();
        if (params != null) {
            for (Parameter param : params) {
                sb.append(param.getName()).append(" ");
                sb.append(param.getSchema().getType()).append(" ");
                if (param.getSchema().getDefault() != null)sb.append("\"").append(param.getSchema().getDefault()).append("\"").append(" ");
                else sb.append(param.getSchema().getDefault()).append(" ");
                sb.append(param.getRequired()).append(sep);
            }
        }
        String ref = "";
        Boolean required = false;
        StringBuilder media = new StringBuilder();
        StringBuilder mediaType = new StringBuilder();
        Object body = null;
        if (operation.getRequestBody() != null) {
            RequestBody requestBody = operation.getRequestBody();
            ref = requestBody.get$ref();
            required = requestBody.getRequired();
            if (required != null && required) {
                Content content = requestBody.getContent();
                for (Map.Entry<String, MediaType> entry : content.entrySet()) {
                    media.append(entry.getKey()).append(sep);
                    mediaType.append(entry.getValue().getSchema().getType()).append(sep);
                }
            }
        }
        count++;
        data.put(Integer.toString(count), new Object[] {
                PATH_CONTEXT + uri,
                operation.getOperationId(),
                method,
                sb.toString(),
                body,
                ref,
                required,
                media.toString(),
                mediaType.toString()
        });
    }

    private static void saveFileExcel(Map<String, Object[]> datatypes) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet(SHEET_NAME);
        int rowNum = 0;
        System.out.println("Creating excel");
        Set<String> keyset = datatypes.keySet();
        for (String key : keyset) {
            Row row = sheet.createRow(rowNum++);
            int colNum = 0;
            Object[] objArr = datatypes.get(key);
            for (Object field : objArr) {
                Cell cell = row.createCell(colNum++);
                if (field instanceof String) {
                    cell.setCellValue((String) field);
                } else if (field instanceof Double) {
                    cell.setCellValue((Double) field);
                } else if (field instanceof Integer) {
                    cell.setCellValue((Integer) field);
                } else if (field instanceof Boolean) {
                    cell.setCellValue((Boolean) field);
                } else if (field instanceof Date) {
                    cell.setCellValue((Date) field);
                } else if (field instanceof Calendar) {
                    cell.setCellValue((Calendar) field);
                } else if (field instanceof RichTextString) {
                    cell.setCellValue((RichTextString) field);
                } else if (field instanceof LocalDate) {
                    cell.setCellValue((LocalDate) field);
                } else if (field instanceof LocalDateTime) {
                    cell.setCellValue((LocalDateTime) field);
                }
                else cell.setBlank();
            }
        }

        try {
            workbook.write(new FileOutputStream(FILE_NAME));
            System.out.println(FILE_NAME);
            workbook.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        System.out.println("Done");
    }*/
}
