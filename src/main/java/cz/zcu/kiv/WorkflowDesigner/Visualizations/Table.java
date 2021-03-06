package cz.zcu.kiv.WorkflowDesigner.Visualizations;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.List;

/***********************************************************************************************************************
 *
 * This file is part of the Workflow Designer project

 * ==========================================
 *
 * Copyright (C) 2018 by University of West Bohemia (http://www.zcu.cz/en/)
 *
 ***********************************************************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 ***********************************************************************************************************************
 *
 * Table, 2018/17/05 6:32 Joey Pinto
 *
 * This file hosts the Table data type required to return a table as the output of a workflow block
 **********************************************************************************************************************/

public class Table implements Serializable {
    private String caption;
    private List<String> columnHeaders;
    private List<String> rowHeaders;
    private List<List<String>>rows;

    public List<String> getColumnHeaders() {
        return columnHeaders ;
    }

    public void setColumnHeaders(List<String> columnHeaders) {
        this.columnHeaders = columnHeaders;
    }

    public List<String> getRowHeaders() {
        return rowHeaders;
    }

    public void setRowHeaders(List<String> rowHeaders) {
        this.rowHeaders = rowHeaders;
    }

    public List<List<String>> getRows() {
        return rows;
    }

    public void setRows(List<List<String>> rows) {
        this.rows = rows;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    /**
     * getHTML -Joey Pinto
     * @return An HTML String representing the table data
     */
    public String getHTML(){
        StringBuilder html=new StringBuilder();
        html.append("<table border=\"1\">\n");

        if(caption!=null&&!caption.isEmpty()){
            html.append("<caption>"+getCaption()+"</caption>");
        }

        boolean hasColumnHeaders = getColumnHeaders()!=null&&getColumnHeaders().size()>0;
        boolean hasRowHeaders = getRowHeaders()!=null&&getRowHeaders().size()>0;
        if(hasColumnHeaders){
            html.append("<tr>");
            if(hasRowHeaders){
                html.append("<th></th>");
            }
            for(String header:getColumnHeaders()){
                html.append("<th>"+header+"</th>");
            }
            html.append("</tr>\n");
        }

        for(int i=0;i<rows.size();i++){
            html.append("<tr>");
            if(hasRowHeaders){
                html.append("<th>"+getRowHeaders().get(i)+"</th>");
            }
            for(String col:rows.get(i)){
                html.append("<td>"+col+"</td>");
            }
            html.append("</tr>\n");
        }
        html.append("</table>");
        return html.toString();
    }

    public JSONObject toJSON(){
        JSONObject table = new JSONObject();
        JSONArray data=new JSONArray();

        boolean hasColumnHeaders = getColumnHeaders()!=null&&getColumnHeaders().size()>0;
        boolean hasRowHeaders = getRowHeaders()!=null&&getRowHeaders().size()>0;

        if(hasColumnHeaders){
            JSONArray headerRow = new JSONArray();
            if(hasRowHeaders){
                headerRow.put("");
            }
            for(String header:getColumnHeaders()){
                headerRow.put(header);
            }
            data.put(headerRow);
        }

        for(int i=0;i<rows.size();i++){
            JSONArray row = new JSONArray();
            if(hasRowHeaders){
                row.put(getRowHeaders().get(i));
            }
            for(String col:rows.get(i)){
                row.put(col);
            }
            data.put(row);
        }
        table.put("data",data);
        return table;
    }

    public String toCSV() {
        JSONArray rows=toJSON().getJSONArray("data");
        StringBuilder csv=new StringBuilder();
        for(int i=0;i<rows.length();i++){
            JSONArray columns=rows.getJSONArray(i);
            for(int j=0;j<columns.length();j++){
                csv.append(columns.get(j));
                if(j<columns.length()-1)csv.append(",");
                else csv.append("\n");
            }
        }
        return csv.toString();
    }
}
