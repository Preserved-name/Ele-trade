package com.jiwuji.agent;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;

/**
 * Excel 历史电价数据读取器
 * 读取交易时段和成交均价两列数据
 */
@Slf4j
@Component
public class ExcelDataReader {

    @Value("${excel.data.path:classpath:data/history_price.xlsx}")
    private Resource excelResource;

    private List<PriceRecord> historicalData = new ArrayList<>();

    /**
     * 电价记录
     */
    public record PriceRecord(String timeSlot, double avgPrice) {}

    @PostConstruct
    public void loadExcelData() {
        try {
            log.info("正在加载历史电价数据: {}", excelResource.getFilename());
            
            try (InputStream is = excelResource.getInputStream();
                 Workbook workbook = new XSSFWorkbook(is)) {
                
                Sheet sheet = workbook.getSheetAt(0);
                log.info("Excel 文件共 {} 行数据", sheet.getLastRowNum());
                
                // 只读取前24行数据（索引1-24）
                int maxRows = Math.min(24, sheet.getLastRowNum());
                for (int i = 1; i <= maxRows; i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    
                    try {
                        // 第2列：交易时段（索引1）
                        Cell timeSlotCell = row.getCell(1);
                        String timeSlot = getCellValueAsString(timeSlotCell);
                        
                        // 第10列：成交均价（索引9）
                        Cell avgPriceCell = row.getCell(10);
                        Double avgPrice = getCellValueAsDouble(avgPriceCell);
                        
                        if (timeSlot != null && avgPrice != null) {
                            historicalData.add(new PriceRecord(timeSlot, avgPrice));
                        }
                    } catch (Exception e) {
                        log.warn("读取第 {} 行数据失败: {}", i + 1, e.getMessage());
                    }
                }
                
                log.info("成功加载 {} 条电价记录（第1-24行）", historicalData.size());
                
                // 打印示例数据
                if (!historicalData.isEmpty()) {
                    log.info("示例数据: 时段={}, 均价={}",
                        historicalData.get(0).timeSlot(), 
                        historicalData.get(0).avgPrice());
                }
                
            }
            
        } catch (Exception e) {
            log.error("加载 Excel 数据失败，将使用空数据集", e);
        }
    }

    /**
     * 获取指定日期和时段的历史价格
     * 返回所有匹配该时段的均价列表
     */
    public List<Double> getHistoricalPricesByTimeSlot(String timeSlot) {
        return historicalData.stream()
            .filter(record -> record.timeSlot().equals(timeSlot))
            .map(PriceRecord::avgPrice)
            .toList();
    }

    /**
     * 获取所有历史数据（格式化后）
     * 用于提供给 LLM 分析
     */
    public String getFormattedHistoricalData() {
        if (historicalData.isEmpty()) {
            return "无历史数据";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("最新一天电价数据：\n");
        
        for (PriceRecord record : historicalData) {
            sb.append(String.format("时段: %s, 成交均价: %.2f 元/兆瓦时\n", 
                record.timeSlot(), record.avgPrice()));
        }
        
        return sb.toString();
    }

    /**
     * 获取所有唯一时段（24小时）
     */
    public List<String> getAllTimeSlots() {
        return historicalData.stream()
            .map(PriceRecord::timeSlot)
            .distinct()
            .sorted()
            .toList();
    }

    /**
     * 获取单元格值为字符串
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                // 如果是日期格式
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                // 数字转字符串
                double numericValue = cell.getNumericCellValue();
                // 如果是整数
                if (numericValue == Math.floor(numericValue)) {
                    return String.valueOf((long) numericValue);
                }
                return String.valueOf(numericValue);
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }

    /**
     * 获取单元格值为 Double
     */
    private Double getCellValueAsDouble(Cell cell) {
        if (cell == null) return null;
        
        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return cell.getNumericCellValue();
                case STRING:
                    String strValue = cell.getStringCellValue().trim();
                    if (strValue.isEmpty()) return null;
                    return Double.parseDouble(strValue);
                case FORMULA:
                    return cell.getNumericCellValue();
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
