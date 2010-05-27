/*
 *  Copyright (c) 2009 Ondrej Dusek
 *  All rights reserved.
 * 
 *  Redistribution and use in source and binary forms, with or without modification, 
 *  are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright notice, this list 
 *  of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, this 
 *  list of conditions and the following disclaimer in the documentation and/or other 
 *  materials provided with the distribution.
 *  Neither the name of Ondrej Dusek nor the names of their contributors may be
 *  used to endorse or promote products derived from this software without specific 
 *  prior written permission.
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND 
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED 
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. 
 *  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 *  INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, 
 *  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE 
 *  OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package en_deep.mlprocess.manipulation;

import en_deep.mlprocess.Logger;
import en_deep.mlprocess.Task;
import en_deep.mlprocess.exception.TaskException;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Vector;
import java.util.Hashtable;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;

/**
 * This class merges several data sets into one.
 * @author Ondrej Dusek
 */
public class DataMerger extends Task {

    /* CONSTANTS */
    
    /** Line feed character */
    private static final String LF = System.getProperty("line.separator");

    /* DATA */

    /* METHODS */

    /**
     * This creates a new {@link DataMerger} task. It doesn't take any parameter except the
     * input and output data sets' descriptions. Therefore, the number of output
     * data sources must be divisible by the number of input data sources.
     *
     * @param id the task id
     * @param parameters have no sense here
     * @param input the input data sets or files
     * @param output the output data sets or files
     */
    public DataMerger(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output) {
        super(id, parameters, input, output);

        if (parameters.size() > 0){
            Logger.getInstance().message("DataMerger parameters are ignored", Logger.V_WARNING);
        }
    }


    /**
     * Tries to merge the input sources to the output sources.
     * Checks if the number of inputs is divisible by the number of outputs, then tries to read all the
     * inputs and write the outputs.
     *
     * @throws TaskException for wrong number of inputs, or if an I/O error occurs
     */
    @Override
    public void perform() throws TaskException {

        int ratio = this.input.size() / this.output.size();

        if (this.input.size() == 0 || this.input.size() % this.output.size() !=  0){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }

        for (int j = 0; j < this.output.size(); ++j){

            try {
                this.mergeData(this.input.subList(ratio * j, ratio * j + ratio), this.output.get(j));
            }
            catch(Exception e){
                Logger.getInstance().message(this.id + ": I/O Error:" + e.getMessage(), Logger.V_IMPORTANT);
                throw new TaskException(TaskException.ERR_IO_ERROR, this.id);
            }
        }
    }

    /**
     * Tries to merge several input files into one output, using WEKA code.
     *
     * @param in the list of input files to be merged
     * @param out the output file to write to
     */
    private void mergeData(List<String> in, String out) throws Exception {

        ConverterUtils.DataSource data = new ConverterUtils.DataSource(in.get(0));
        FileOutputStream os = new FileOutputStream(out);
        Instances firstStructure = data.getStructure();

        Logger.getInstance().message(this.id + ": adding " + in.get(0) + " to " + out + "...", Logger.V_DEBUG);

        // write the first file, including the headers
        os.write(firstStructure.toString().getBytes("UTF-8"));

        while (data.hasMoreElements(firstStructure)){
            os.write(data.nextElement(firstStructure).toString().getBytes("UTF-8"));
            os.write(LF.getBytes());
        }

        data.reset();

        // add the other files
        for (int i = 1; i < in.size(); ++i){

            data = new ConverterUtils.DataSource(in.get(i));
            Instances structure = data.getStructure();
            String errMsg;

            Logger.getInstance().message(this.id + ": adding " + in.get(i) + " to " + out + "...", Logger.V_DEBUG);

            // check for equal data format
            if ((errMsg = structure.equalHeadersMsg(firstStructure)) != null){
                Logger.getInstance().message(this.id + ": cannot merge -- headers differ: " + errMsg, Logger.V_IMPORTANT);
                throw new TaskException(TaskException.ERR_INVALID_DATA, this.id);
            }

            while (data.hasMoreElements(structure)){
                os.write(data.nextElement(structure).toString().getBytes("UTF-8"));
                os.write(LF.getBytes());
            }
            data.reset();
        }

        os.close();
    }


}