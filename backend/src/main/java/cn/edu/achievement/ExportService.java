package cn.edu.achievement;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.*;
import static cn.edu.achievement.Rows.*;

@Service
public class ExportService {
    final AppService app;final Database db;final FileService files;
    private static final Map<String,String> TYPES=Map.of("TEACHING","教学成果","RESEARCH","科研成果","COMPETITION","学科竞赛","SCHOLARSHIP","奖学金","PATENT","专利","PAPER","论文","SOFTWARE","软件著作权","CERTIFICATION","行业认证");
    ExportService(AppService app,Database db,FileService files){this.app=app;this.db=db;this.files=files;}
    List<Achievement> selected(Map<String,String> q){List<Achievement> list=app.filtered(q,true);if(list.size()>10000)throw ApiException.bad("单次最多导出10000项成果，请缩小筛选范围");return list;}
    byte[] xlsx(Map<String,String> q) throws IOException {return workbook(selected(q));}
    byte[] workbook(List<Achievement> list) throws IOException {
        try(XSSFWorkbook book=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            Sheet sheet=book.createSheet("成果清单");String[] headers={"成果编号","成果名称","成果类型","级别","获得日期","审核状态","填报人","参与人与指导教师","支撑指标","分类详情","描述","附件数"};
            Font font=book.createFont();font.setBold(true);font.setColor(IndexedColors.WHITE.getIndex());CellStyle style=book.createCellStyle();style.setFont(font);style.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Row header=sheet.createRow(0);for(int i=0;i<headers.length;i++){header.createCell(i).setCellValue(headers[i]);header.getCell(i).setCellStyle(style);sheet.setColumnWidth(i,i==1?48*256:24*256);}
            int index=1;for(Achievement a:list){Row r=sheet.createRow(index++);String people=db.participants(a.id).stream().map(p->db.user(p.userId).name+"("+p.role+")").collect(Collectors.joining("；"));String indicators=db.achievementIndicators(a.id).stream().map(i->db.indicator(i).code+" "+db.indicator(i).name).collect(Collectors.joining("；"));
                Object[] values={a.id,a.title,TYPES.get(a.type),a.level,a.achievedOn.toString(),a.status,db.user(a.ownerId).name,people,indicators,a.detailsJson,a.description,db.attachments(a.id).size()};
                for(int i=0;i<values.length;i++){Cell c=r.createCell(i);if(values[i] instanceof Number n)c.setCellValue(n.doubleValue());else c.setCellValue(Objects.toString(values[i],""));}
            }sheet.createFreezePane(0,1);sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,Math.max(0,list.size()),0,headers.length-1));book.write(out);return out.toByteArray();
        }
    }
    byte[] zip(Map<String,String> q) throws IOException {
        List<Achievement> selected=selected(q);long total=0;for(Achievement a:selected)for(Attachment f:db.attachments(a.id))total+=f.size;
        if(total>100L*1024*1024)throw new ApiException(413,"单次材料导出不能超过100MB，请缩小筛选范围");
        try(ByteArrayOutputStream bytes=new ByteArrayOutputStream();ZipOutputStream zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)){
            put(zip,"成果清单.xlsx",workbook(selected));StringBuilder manifest=new StringBuilder("\uFEFF成果编号,成果名称,成果类型,附件编号,原始文件名,材料路径\r\n");
            for(Achievement a:selected){for(Attachment f:db.attachments(a.id)){
                String path=TYPES.get(a.type)+"/"+a.id+"/"+f.id+"_"+f.originalName;put(zip,path,files.bytes(f));
                manifest.append(csv(a.id)).append(',').append(csv(a.title)).append(',').append(csv(TYPES.get(a.type))).append(',').append(csv(f.id)).append(',').append(csv(f.originalName)).append(',').append(csv(path)).append("\r\n");
            }}put(zip,"材料索引.csv",manifest.toString().getBytes(StandardCharsets.UTF_8));put(zip,"使用说明.txt","材料按成果类型和编号归档；材料索引与成果清单可用于追溯支撑指标。数据遵循当前用户的访问权限和筛选条件，默认仅导出审核通过成果。".getBytes(StandardCharsets.UTF_8));zip.finish();return bytes.toByteArray();
        }
    }
    static String csv(Object value){String s=Objects.toString(value,"");if(s.matches("^[=+@\\-].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
    static void put(ZipOutputStream zip,String name,byte[] content)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(content);zip.closeEntry();}
}
