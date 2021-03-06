package cz.zcu.kiv.WorkflowDesigner;

import cz.zcu.kiv.WorkflowDesigner.Annotations.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.reflections.Reflections;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.nio.charset.Charset;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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
 * Block, 2018/16/05 13:32 Joey Pinto
 *
 * This file is the model for a single block in the workflow designer tool
 **********************************************************************************************************************/


public class Block {
    private Workflow workflow;
    private String name;
    private String family;
    private String module;
    private String description;
    private boolean jarExecutable;
    private Map<String, Data> input;
    private Map<String, Data> output;
    private Map<String,Property> properties;

    //Actual Object instance maintained as context
    private Object context;

    private static Log logger = LogFactory.getLog(Block.class);

    public Object getContext() {
        return context;
    }

    public void setContext(Object context) {
        this.context = context;
    }

    private boolean processed=false;

    /**
     * @param blockObject - Initialize a Block object from a JSON representation
     */
    public void fromJSON(JSONObject blockObject) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException, FieldMismatchException {
        this.name = blockObject.getString("type");
        this.module = blockObject.getString("module");

        //Get block definition from workflow
        Block block = workflow.getDefinition(this.name);

        if(block==null){
            logger.error("Could not find definition of block type "+this.name);
            throw new FieldMismatchException(this.name,"block type");
        }

        this.family = block.getFamily();
        this.properties = block.getProperties();
        this.input = block.getInput();
        this.output = block.getOutput();
        this.jarExecutable = block.isJarExecutable();


        JSONObject values = blockObject.getJSONObject("values");

        //Map properties to object parameters
        for(String key:this.properties.keySet()){
            if(values.has(key)){
                for (Field f: context.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    BlockProperty blockProperty = f.getAnnotation(BlockProperty.class);
                    if (blockProperty != null) {
                        if(blockProperty.name().equals(key)){
                            properties.put(blockProperty.name(), new Property(blockProperty.name(), blockProperty.type(), blockProperty.defaultValue(), blockProperty.description()));

                            //Assign object attributes from properties
                            if(blockProperty.type().endsWith("[]")){

                                //Dealing with List properties
                                if(f.getType().isArray()){
                                    //Unsupported by reflection
                                    throw new IllegalAccessException("Arrays Not supported, Use List instead");
                                }
                                List<Object>components=new ArrayList<>();
                                JSONArray array=values.getJSONArray(key);
                                ParameterizedType listType = (ParameterizedType) f.getGenericType();
                                Class<?> listClass = (Class<?>) listType.getActualTypeArguments()[0];
                                for(int i=0;i<array.length();i++){
                                    if(listClass.equals(File.class)){
                                        components.add(new File(workflow.getRemoteDirectory()+File.separator+array.get(i)));
                                    }
                                    else components.add(array.get(i));
                                }
                                f.set(context,components);
                            }
                            else f.set(context,getFieldFromJSON(f,values, key));
                            break;
                        }
                    }
                }
            }
        }

        logger.info("Instantiated "+getName()+" block from Workflow");

    }

    /**
     * Get value of actual property field
     * @param f - Relfection field
     * @param values - JSON object containing values assigned to properties
     * @param key - Key to get Field
     * @return Actual object instance
     */
    private Object getFieldFromJSON(Field f, JSONObject values, String key) {
        try {
            if (f.getType().equals(int.class) || f.getType().equals(Integer.class))
                return values.getInt(key);
            else if (f.getType().equals(double.class) || f.getType().equals(Double.class))
                return values.getDouble(key);
            else if (f.getType().equals(boolean.class) || f.getType().equals(Boolean.class))
                return values.getBoolean(key);
            else if (f.getType().equals(File.class))
                //If type is file, instance of the actual file is passed rather than just the location
                return new File(workflow.getRemoteDirectory() + File.separator + values.getString(key));

            return f.getType().cast(values.getString(key));
        }
        catch (Exception e){
            //Unpredictable result when reading from a field
            logger.error(e);
            return null;
        }
    }

    /**
     * Block constructor
     * @param context - Object instance
     * @param workflow - Workflow instance
     */
    public Block(Object context, Workflow workflow){
        this.context = context;
        this.workflow = workflow;
    }


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Convert Block to JSON for front-end
     */
    public JSONObject toJSON(){
        JSONObject blockJs=new JSONObject();
        blockJs.put("name",getName());
        blockJs.put("family", getFamily());
        blockJs.put("module", getModule());
        blockJs.put("description", getDescription());
        JSONArray fields=new JSONArray();
        for(String key:properties.keySet()){
            Property property=properties.get(key);
            JSONObject field=new JSONObject();
            field.put("name",property.getName());
            field.put("type",property.getType());
            field.put("defaultValue",property.getDefaultValue());
            field.put("attrs","editable");
            field.put("description",property.getDescription());
            fields.put(field);
        }

        if(input!=null && input.size()!=0) {
            for(String inputParam:input.keySet()) {
                Data inputValue=input.get(inputParam);
                JSONObject inputObj = new JSONObject();
                inputObj.put("name", inputValue.getName());
                inputObj.put("type", inputValue.getType());
                inputObj.put("attrs", "input");
                inputObj.put("card", inputValue.getCardinality());
                fields.put(inputObj);
            }
        }

        if(output!=null && output.size()!=0) {
            for(String outputParam:output.keySet()){
                Data outputValue=output.get(outputParam);
                JSONObject outputObj = new JSONObject();
                outputObj.put("name", outputValue.getName());
                outputObj.put("type", outputValue.getType());
                outputObj.put("attrs", "output");
                outputObj.put("card", outputValue.getCardinality());
                fields.put(outputObj);
            }
        }
        blockJs.put("fields", fields);

        return blockJs;
    }


    /**
     * Process a block and return value returned by BlockExecute block
     * @param blocks Map of blocks
     * @param fields Mapping of fieldName to actual field
     * @param stdOut Standard Output stream builder
     * @param stdErr Error Stream builder
     * @return Object returned by BlockExecute method
     * @throws Exception
     */
    public Object processBlock(Map<Integer,Block> blocks, Map<String,InputField> fields, StringBuilder stdOut, StringBuilder stdErr) throws Exception {
       Object output;
        BlockData blockData=new BlockData(getName());

        logger.info("Processing a "+getName()+" block");

        //Assign inputs to the instance
        assignInputs(blocks,fields,blockData);

        if(isJarExecutable() && workflow.getJarDirectory()!=null){
            //Execute block as an external JAR file
            output = executeAsJar(blockData,stdOut,stdErr);
        }
        else{
            logger.info("Executing "+getName()+" block natively");
            try {
                //Execute block natively in the current class loader
                output = process();
            }
            catch (Exception e){
                e.printStackTrace();
                stdErr.append(ExceptionUtils.getRootCauseMessage(e)+" \n");
                for(String trace:ExceptionUtils.getRootCauseStackTrace(e)){
                    stdErr.append(trace+" \n");
                }
                logger.error("Error executing "+getName()+" Block natively",e);
                throw e;
            }
        }

        setProcessed(true);
        logger.info("Execution of "+getName()+ " block completed successfully");
        return output;
    }

    /**
     * Execute block externally as a JAR
     *
     * @param blockData Serializable BlockData model representing all the data needed by a block to execute
     * @param stdOut Standard Output Stream
     * @param stdErr Standard Error Stream
     * @return output returned by BlockExecute Method
     * @throws Exception when output file is not created
     */
    private Object executeAsJar(BlockData blockData, StringBuilder stdOut, StringBuilder stdErr) throws Exception {

        Object output;
        logger.info("Executing "+getName()+" as a JAR");
        try {
            String fileName = "obj_" + new Date().getTime() ;
            File inputFile=new File(workflow.getJarDirectory()+File.separator+fileName+".in");
            File outputFile =new File(workflow.getJarDirectory()+File.separator+fileName+".out");

            //Serialize and write BlockData object to a file
            FileOutputStream fos = new FileOutputStream(inputFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(blockData);
            oos.close();

            File jarDirectory = new File(workflow.getJarDirectory());
            jarDirectory.mkdirs();
            String jarFilePath = jarDirectory.getAbsolutePath()+File.separator+getModule().split(":")[0];
            File jarFile = new File(jarFilePath);

            //Calling jar file externally with entry point at the Block's main method
            String[]args=new String[]{"java", "-cp",jarFile.getAbsolutePath() ,"cz.zcu.kiv.WorkflowDesigner.Block",inputFile.getAbsolutePath(),outputFile.getAbsolutePath(),getModule().split(":")[1]};
            ProcessBuilder pb = new ProcessBuilder(args);

            //Store output and error streams to files
            logger.info("Executing jar file "+jarFilePath);
            File stdOutFile = new File("std_output.log");
            pb.redirectOutput(stdOutFile);
            File stdErrFile = new File("std_error.log");
            pb.redirectError(stdErrFile);
            Process ps = pb.start();
            ps.waitFor();
            stdOut.append(FileUtils.readFileToString(stdOutFile,Charset.defaultCharset()));
            stdErr.append(FileUtils.readFileToString(stdErrFile,Charset.defaultCharset()));
            InputStream is=ps.getErrorStream();
            byte b[]=new byte[is.available()];
            is.read(b,0,b.length);
            String errorString = new String(b);
            if(!errorString.isEmpty()){
                logger.error(errorString);
                stdErr.append(errorString);
            }

            is=ps.getInputStream();
            b=new byte[is.available()];
            is.read(b,0,b.length);
            String outputString = new String(b);
            if(!outputString.isEmpty()){
                logger.info(outputString);
                stdOut.append(outputString);
            }

            FileUtils.deleteQuietly(inputFile);

            if(outputFile.exists()){
                FileInputStream fis = new FileInputStream(outputFile);
                ObjectInputStream ois = new ObjectInputStream(fis);
                blockData = (BlockData) ois.readObject();
                ois.close();
                output=blockData.getProcessOutput();
                FileUtils.deleteQuietly(outputFile);
                for (Field f: context.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
                    if (blockOutput != null) {
                        f.set(context,blockData.getOutput().get(blockOutput.name()));
                    }
                }
            }
            else{
                throw new Exception("Output file does not exist");
            }


        }
        catch (Exception e) {
            e.printStackTrace();
            stdErr.append(org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e));
            logger.error("Error executing Jar file", e);
            throw e;
        }
        return output;
    }

    /**
     * Assign Inputs - Maps output fields of previous block to input fields of next block and initializes properties
     * @param blocks
     * @param fields
     * @param blockData
     * @throws FieldMismatchException
     * @throws IllegalAccessException
     * @throws ClassNotFoundException
     */
    private void assignInputs(Map<Integer,Block> blocks, Map<String,InputField> fields, BlockData blockData) throws FieldMismatchException, IllegalAccessException, ClassNotFoundException {
        //Assign properties to object instance
        for (Field f: context.getClass().getDeclaredFields()) {
            f.setAccessible(true);

            BlockProperty blockProperty = f.getAnnotation(BlockProperty.class);
            if (blockProperty != null) {
                if(blockProperty.type().equals(Type.FILE)){
                    blockData.getProperties().put(blockProperty.name(),new File(workflow.getRemoteDirectory()+File.separator+f.get(context)));
                }
                else blockData.getProperties().put(blockProperty.name(),f.get(context));
            }
        }

        if(getInput()!=null&&getInput().size()>0) {

            //Assigning inputs

            for (String key : getInput().keySet()) {
                InputField field = fields.get(key);
                if(field==null){
                    //Missing input field
                    continue;
                }
                //Getting destination for data
                Data destinationData=getInput().get(key);

                int inputCardinality = field.getSourceParam().size();
                List<Object> components=new ArrayList<>();

                for(int i=0;i<inputCardinality;i++){
                    Object value = null;
                    String sourceParam = field.getSourceParam().get(i);
                    int sourceBlockId = field.getSourceBlock().get(i);
                    Block sourceBlock = blocks.get(sourceBlockId);
                    if(field.getSourceBlock()==null){
                        logger.error("Could not find the source block for the input "+key+" for a "+getName()+" block");
                        throw new FieldMismatchException(key,"source");
                    }
                    Map<String, Data> source = sourceBlock.getOutput();
                    Data sourceData=null;

                    if(source.containsKey(sourceParam)){
                        sourceData=source.get(sourceParam);
                    }
                    if(sourceData==null) {
                        throw new FieldMismatchException(key,"source");
                    }

                    for (Field f: sourceBlock.getContext().getClass().getDeclaredFields()) {
                        f.setAccessible(true);

                        BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
                        if (blockOutput != null){
                            if(blockOutput.name().equals(sourceData.getName())) {
                                value = f.get(sourceBlock.getContext());
                                break;
                            }
                        }
                    }

                    components.add(value);

                }


                //Assigning outputs to destination
                for (Field f: context.getClass().getDeclaredFields()) {
                    f.setAccessible(true);

                    BlockInput blockInput = f.getAnnotation(BlockInput.class);
                    if (blockInput != null) {
                        if(blockInput.name().equals(destinationData.getName())){
                            if(!blockInput.type().endsWith("[]")){
                                Object val=components.get(0);
                                f.set(context,val);
                                blockData.getInput().put(destinationData.getName(),val);
                            }
                            else{
                                if(f.getType().isArray()){
                                    throw new IllegalAccessException("Arrays not supported, Use Lists Instead");
                                }
                                f.set(context,f.getType().cast(components));
                                blockData.getInput().put(destinationData.getName(),components);
                            }

                            break;
                        }
                    }
                }

            }
        }


    }

    /**
     * process - execute a method annotated by Block Execute annotation
     * @return whatever object is returned by the method
     */
    public Object process() throws InvocationTargetException, IllegalAccessException {
        Object output = null;
        for(Method method:context.getClass().getDeclaredMethods()){
            method.setAccessible(true);
            if(method.getAnnotation(BlockExecute.class)!=null){
                    output =  method.invoke(context);
                    break;
            }
        }
        return output;
    }

    public Map<String, Property> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Property> properties) {
        this.properties = properties;
    }

    public Map<String, Data> getInput() {
        return input;
    }

    public void setInput(Map<String, Data> input) {
        this.input = input;
    }

    public Map<String, Data> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Data> output) {
        this.output = output;
    }

    public boolean isProcessed() {
        return processed;
    }

    public void setProcessed(boolean processed) {
        this.processed = processed;
    }

    /**
     * Initialize a Block from a class
     */
    public void initialize(){
        if(getProperties()==null)
            setProperties(new HashMap<String,Property>());
        if(getInput()==null)
            setInput(new HashMap<String, Data>());
        if(getOutput()==null)
            setOutput(new HashMap<String, Data>());

        for (Field f: context.getClass().getDeclaredFields()) {
            f.setAccessible(true);

            BlockProperty blockProperty = f.getAnnotation(BlockProperty.class);
            if (blockProperty != null){
                properties.put(blockProperty.name(),new Property(blockProperty.name(),blockProperty.type(),blockProperty.defaultValue(), blockProperty.description()));
            }

            BlockInput blockInput = f.getAnnotation(BlockInput.class);
            if (blockInput != null){
                String cardinality="";
                if(blockInput.type().endsWith("[]")){
                    cardinality=WorkflowCardinality.MANY_TO_MANY;
                }
                else{
                    cardinality=WorkflowCardinality.ONE_TO_ONE;
                }
                input.put(blockInput.name(),new Data(blockInput.name(),blockInput.type(),cardinality));
            }

            BlockOutput blockOutput = f.getAnnotation(BlockOutput.class);
            if (blockOutput != null){
                output.put(blockOutput.name(),new Data(blockOutput.name(),blockOutput.type(),WorkflowCardinality.MANY_TO_MANY));
            }
        }
        logger.info("Initialized "+getName()+" block from annotations");

    }

    /**
     *  Externally access main function, modification of parameters will affect reflective access
     *
     * @param args 1) serialized input file 2) serialized output file 3) Package Name
     */
    public static void main(String[] args){

        try {
            //Reading BlockData object from file
            BlockData blockData = SerializationUtils.deserialize(FileUtils.readFileToByteArray(new File(args[0])));

            Set<Class<?>> blockTypes = new Reflections(args[2]).getTypesAnnotatedWith(BlockType.class);

            Class type = null;

            for (Class blockType : blockTypes) {
                Annotation annotation = blockType.getAnnotation(BlockType.class);
                Class<? extends Annotation>  currentType = annotation.annotationType();

                String blockTypeName = (String) currentType.getDeclaredMethod("type").invoke(annotation, (Object[]) null);
                if (blockData.getName().equals(blockTypeName)) {
                    type=blockType;
                    break;
                }
            }

            Object obj;
            if(type!=null){
                obj=type.newInstance();
            }
            else{
                logger.error("No classes with Workflow Designer BlockType Annotations were found!");
                throw new Exception("Error Finding Annotated Class");
            }


            for(Field field:type.getDeclaredFields()){
                field.setAccessible(true);
                if(field.getAnnotation(BlockInput.class)!=null){
                    field.set(obj,blockData.getInput().get(field.getAnnotation(BlockInput.class).name()));
                }
                else if(field.getAnnotation(BlockProperty.class)!=null){
                    field.set(obj,blockData.getProperties().get(field.getAnnotation(BlockProperty.class).name()));
                }
            }


            Method executeMethod = null;

            for(Method m:type.getDeclaredMethods()){
                m.setAccessible(true);
                if(m.getAnnotation(BlockExecute.class)!=null){
                    executeMethod=m;
                    break;
                }
            }

            if(executeMethod!=null){
                Object outputObj=executeMethod.invoke(obj);
                blockData.setProcessOutput(outputObj);
            }
            else{
                logger.error("No method annotated with Workflow Designer BlockExecute was found");
                throw new Exception("Error finding Execute Method");
            }

            blockData.setOutput(new HashMap<String, Object>());

            for(Field field:type.getDeclaredFields()){
                field.setAccessible(true);
                if(field.getAnnotation(BlockOutput.class)!=null){
                    blockData.getOutput().put(field.getAnnotation(BlockOutput.class).name(),field.get(obj));
                }

            }

            //Write output object to file
            FileOutputStream fos = FileUtils.openOutputStream(new File(args[1]));
            SerializationUtils.serialize(blockData,fos);
            fos.close();

        }
        catch (Exception e){
            logger.error(org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(e));
        }
    }

    public boolean isJarExecutable() {
        return jarExecutable;
    }

    public void setJarExecutable(boolean jarExecutable) {
        this.jarExecutable = jarExecutable;
    }
}
