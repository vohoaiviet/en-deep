/*
 *  Copyright (c) 2010 Ondrej Dusek
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
import en_deep.mlprocess.exception.TaskException;
import en_deep.mlprocess.utils.StringUtils;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.Vector;
import weka.core.Attribute;
import weka.core.Instances;

/**
 * This creates new attributes that contain only the most common values of the old ones. Everything else is
 * filtered out of the old attributes.
 *
 * @author Ondrej Dusek
 */
public class AttributeFilter extends MergedHeadersOutput {

    /* CONSTANTS */

    /** The "most_common" parameter name */
    private static final String MOST_COMMON = "most_common";
    /** The "min_occurrences" parameter name */
    private static final String MIN_OCCURRENCES = "min_occurrences";
    /** The 'min_percentage' parameter name */
    private static final String MIN_PERCENTAGE = "min_percentage";
    /** The "del_orig" parameter name */
    private static final String DEL_ORIG = "del_orig";
    /** The "attributes" parameter name */
    private static final String ATTRIBUTES = "attributes";

    /** The string that is appended to all filtered attribute names */
    private static final String ATTR_NAME_SUFF = "_filt";

    /** The attribute value to replace all the filtered-out values */
    private static final String OTHER_VALUE = "[OTHER]";

    /* DATA */

    /** Delete the original attributePrefixes ? */
    private boolean delOrig;
    /** Merge the inputs before processing ? */
    private boolean mergeInputs;
    /** How many most common feature values should be kept ? (-1 = not applied) */
    private int mostCommon = -1;
    /** What's the minimum number of occurrences a value should have to be preserved ? (-1 = not applied) */
    private int minOccurrences = -1;
    /** What's the minimum percentage of occurrences in relation to the total number of instances, so 
     * that the value is preserved ? */
    private double minPercentage = Double.NaN;
    /** The names of the attributePrefixes to be filtered */
    private String [] attributePrefixes;


    /* METHODS */

    /**
     * This creates a new {@link AttributeFilter}. The class must have the same number of
     * inputs and outputs and no wildcards in file names.
     * <p>
     * There is one compulsory parameter:
     * </p>
     * <ul>
     * <li><tt>attributes</tt> -- space-separated list of prefixes of attributes that should be filtered</li>
     * </ul>
     * <p>
     * There are two parameters, one of which must be set:
     * </p>
     * <ul>
     * <li><tt>most_common</tt> -- the maximum number of most common values that will be preserved</li>
     * <li><tt>min_occurrences</tt> -- minimum number of occurrences that the values must have in order to be
     *  preserved</li>
     * <li><tt>min_percentage</tt> -- the minimum percentage the given value must take up in all instances in
     * order to be preserved</li>
     * </ul>
     * <p>
     * If more parameters are set, all conditions must be fulfilled, so that the value is not discarded.
     * </p><p>
     * There are additional parameters:
     * <p>
     * <ul>
     * <li><tt>del_orig</tt> -- delete the original attributePrefixes and keep only the filtered</li>
     * <li><tt>merge_inputs</tt> -- merge all the inputs before processing (inputs are assumed to have the same format,
     *      including the possible values</li>
     * <li><tt>info_file</tt> -- if set, the number of inputs must be one/twice bigger (depends on <tt>merge_inputs</tt>)
     * and the last input(s) are considered to be saved information about the filtering process. This will
     * then ignore all other parameters (except <tt>del_orig</tt> and <tt>merge_inputs</tt>) and perform the process
     * exactly as instructed in the file.</li>
     * <li><tt>output_info</tt> -- if set, the number of outputs must be one/twice bigger (depends on <tt>merge_inputs</tt>)
     * and the last outputs(s) are considered to be output files where the processing info about this filtering
     * is saved for later use.</tt>
     * </ul>
     */
    public AttributeFilter(String id, Hashtable<String, String> parameters, Vector<String> input, Vector<String> output)
            throws TaskException {
        
        super(id, parameters, input, output);

        // check parameters
        if (this.hasParameter(MOST_COMMON)){
            this.mostCommon = this.getIntParameterVal(MOST_COMMON);
        }
        if (this.hasParameter(MIN_OCCURRENCES)){
            this.minOccurrences = this.getIntParameterVal(MIN_OCCURRENCES);
        }
        if (this.hasParameter(MIN_PERCENTAGE)){
            this.minPercentage = this.getDoubleParameterVal(MIN_PERCENTAGE) / 100.0;
        }

        if (this.hasParameter(ATTRIBUTES)){
            this.attributePrefixes = this.parameters.get(ATTRIBUTES).split("\\s+");
            this.checkPrefixes();
        }

        this.delOrig = this.getBooleanParameterVal(DEL_ORIG);

        if (!this.hasInfoIn() && (this.attributePrefixes == null
                || (this.mostCommon == -1 && this.minOccurrences == -1 && this.minPercentage == Double.NaN))){
            throw new TaskException(TaskException.ERR_INVALID_PARAMS, this.id, "Missing parameters.");
        }

        // check inputs and outputs
        if (this.input.isEmpty()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_INPUTS, this.id);
        }
        if (this.input.size () != this.output.size()){
            throw new TaskException(TaskException.ERR_WRONG_NUM_OUTPUTS, this.id);
        }
        this.eliminatePatterns(this.input);
        this.eliminatePatterns(this.output);
    }

    /**
     * This filters just one attribute of the given name, which must be present in the data
     * and nominal.
     *
     * @param attrPrefix the name of the attribute
     * @param data the data for which the filtering should apply
     * @return attribute name and a list of allowed values
     * @throws NumberFormatException
     * @throws TaskException
     */
    private String filterAttribute(String attrName, Instances[] data, Set<String> allowedValues) throws
            NumberFormatException, TaskException {

        String newName = attrName + ATTR_NAME_SUFF;

        // create a unique name for the new attribute
        while (data[0].attribute(newName) != null) {
            if (newName.endsWith(ATTR_NAME_SUFF)) {
                newName += "1";
            } else {
                int num = Integer.parseInt(newName.substring(newName.lastIndexOf(ATTR_NAME_SUFF) + ATTR_NAME_SUFF.length()));
                newName.replaceFirst("[0-9]+$", Integer.toString(num + 1));
            }
        }

        allowedValues.add(OTHER_VALUE);

        Attribute newAttr = new Attribute(newName, new Vector<String>(allowedValues));
        for (int i = 0; i < data.length; ++i) {
            this.addFiltered(data[i], attrName, newAttr, allowedValues);
            if (this.delOrig) {
                // delete the old attribute if necessary
                data[i].deleteAttributeAt(data[i].attribute(attrName).index());
            }
        }
        allowedValues.remove(OTHER_VALUE);
        return attrName + ":" + StringUtils.join(allowedValues.toArray(),",", true);
    }


    /**
     * This performs the filtering on all nominal attributes with the given prefix, if applicable.
     * Applies this to all data that may be from different files.
     *
     * @param data the data to be filtered
     * @param attrPrefix the prefix of attributes to be filtered
     * @param list of filtered attributes with valid values (one attribute per line)
     */
    private String filterAttributePrefix(Instances [] data, String attrPrefix) throws TaskException {

        Vector<String> matches = new Vector<String>();
        Enumeration<Attribute> allAttribs = data[0].enumerateAttributes();

        // find all matching nominal attributes
        while (allAttribs.hasMoreElements()){
            Attribute a = allAttribs.nextElement();
            if (a.name().startsWith(attrPrefix) && a.isNominal()){
                matches.add(a.name());
            }
        }

        // no matching attribute found
        if (matches.isEmpty()) {
            // attribute must be found and must be nominal
            Logger.getInstance().message(this.id + " : No attribute matching " + attrPrefix + " found in data "
                    + data[0].relationName(), Logger.V_WARNING);
            return "";
        }

        // filter all matching attributes
        StringBuilder sb = new StringBuilder();
        for (String attrName : matches){

            Vector<String> vals = this.getAttributeValues(data[0], attrName);
            // collect statistics about the given attribute and create its filtered version
            int minOccur = this.getMinOccurrences(data);
            int[] stats = this.collectStatistics(data, attrName);
            Set<String> allowedVals = this.filterValues(stats, minOccur, vals);

            sb.append(this.filterAttribute(attrName, data, allowedVals)).append("\n");
        }
        return sb.toString();
    }

    /**
     * This returns all the values of the given attribute.
     * @param data the data where the attribute is located
     * @param attrName the name of the attribute
     * @return all the possible values of the attribute
     */
    private Vector<String> getAttributeValues(Instances data, String attrName){

        Attribute attr = data.attribute(attrName);
        Vector<String> vals = new Vector<String>(attr.numValues());

        for (int i = 0; i < attr.numValues(); ++i){
            vals.add(attr.value(i));
        }
        return vals;
    }


    /**
     * This collects the statistics about how often the individual values of an attribute appear.
     * @param data the data to be examined
     * @param attrPrefix the attribute to collect the statistics about
     * @return the occurrence counts for all values of the given attribute in the given data
     */
    private int [] collectStatistics(Instances[] data, String attrName) {

        int [] stats = new int [data[0].attribute(attrName).numValues()];

        for (int i = 0; i < data.length; ++i){
            
            double [] values = data[i].attributeToDoubleArray(data[i].attribute(attrName).index());

            for (int j = 0; j < values.length; ++j){
                stats[(int)values[j]]++;
            }
        }

        return stats;

        
    }

    /**
     * Returns the values that have passed the filtering for the given attribute.
     *
     * @param stats the statistics for this attribute
     * @param minOccurrences the minimum number of occurrences the values must have
     * @return true for the values that have passed the filtering, false otherwise
     */
    private Set<String> filterValues(int[] stats, int minOccurrences, Vector<String> vals) {

        HashSet<String> allowedVals = new HashSet<String>();

        if (this.mostCommon > 0){
            
            int [] top = new int [this.mostCommon];

            for (int i = 0; i < top.length; ++i){
                top[i] = -1;
            }
            for (int i = 0; i < stats.length; ++i){
                if (stats[i] > minOccurrences 
                        && (top[this.mostCommon - 1] == -1 || stats[i] > stats[top[this.mostCommon - 1]])){
                    int j = this.mostCommon - 1;
                    while (j > 0 && (top[j] == -1 || stats[i] > stats[top[j-1]])){
                        --j;
                    }
                    System.arraycopy(top, j, top, j + 1, top.length - j - 1);
                    top[j] = i;
                }
            }
            for (int i = 0; i < top.length && top[i] != -1; ++i){
                allowedVals.add(vals.get(top[i]));
            }
        }
        else {
            for (int i = 0; i < stats.length; ++i){
                if (stats[i] > minOccurrences){
                    allowedVals.add(vals.get(i));
                }
            }
        }

        return allowedVals;
    }

    /**
     * Add the filtered attribute and filter the data.
     *
     * @param data the data where the attribute is to be inserted
     * @param attrPrefix the name of the old attribute
     * @param newAttr the new attribute
     * @param allowedValues the allowed values of the old attribute
     */
    private void addFiltered(Instances data, String attrName, Attribute newAttr, Set<String> allowedValues) {

        Attribute oldAttr = data.attribute(attrName);
        int oldIndex = oldAttr.index();
        
        boolean [] allowedIdxs = new boolean [oldAttr.numValues()]; // create map for faster queries
        for (int i = 0; i < oldAttr.numValues(); ++i){
            if (allowedValues.contains(oldAttr.value(i))){
                allowedIdxs[i] = true;
            }
        }

        data.insertAttributeAt(newAttr, oldIndex + 1);
        newAttr = data.attribute(oldIndex + 1);

        for (int i = 0; i < data.numInstances(); ++i){
            int val = (int) data.instance(i).value(oldIndex);
            
            if (allowedIdxs[val]){
                data.instance(i).setValue(newAttr, oldAttr.value(val));
            }
            else {
                data.instance(i).setValue(newAttr, OTHER_VALUE);
            }
        }
    }

    /**
     * Check that some of the attribute prefixes are not prefixes of others, so that the filtering is not
     * processed twice.
     */
    private void checkPrefixes() {

        Vector<Integer> banned = new Vector<Integer>();

        for (int i = 0; i < this.attributePrefixes.length; ++i){
            for (int j = 0; j < i; ++j){
                if (this.attributePrefixes[i].startsWith(this.attributePrefixes[j])){
                    banned.add(i);
                }
                else if (this.attributePrefixes[j].startsWith(this.attributePrefixes[i])){
                    banned.add(j);
                }
            }
        }

        if (banned.size() > 0){
            Logger.getInstance().message(this.id + " : Some prefixes in the 'attributes' parameter overlap.",
                    Logger.V_WARNING);

            String [] uniquePrefixes = new String [this.attributePrefixes.length - banned.size()];
            int pos = 0;

            for (int i = 0; i < this.attributePrefixes.length; ++i){

                if (!banned.contains(i)){
                    uniquePrefixes[pos] = this.attributePrefixes[i];
                    pos++;
                }
            }
            this.attributePrefixes = uniquePrefixes;
        }
    }

    /**
     * This computes the minimum number of occurrences a value must have in order to be preserved, according to
     * the {@link #minOccurrences} and {@link #minPercentage} settings.
     *
     * @param data the data to be processed
     * @return the minimum number of occurrences for the values in data
     */
    private int getMinOccurrences(Instances[] data) {

        if (this.minOccurrences == -1 && this.minPercentage == Double.NaN){
            return 0;
        }
        else if (this.minPercentage == Double.NaN){
            return this.minOccurrences;
        }
        else {
            int sumInst = 0;

            for (int i = 0; i < data.length; ++i){
                sumInst += data[i].numInstances();
            }
            
            return Math.max(this.minOccurrences, (int) Math.ceil(this.minPercentage * sumInst));
        }

    }

    /**
     * This is the main data processing method -- it calls {@link #filterAttributePrefix(Instances[], String)} for
     * all the attribute prefixes. Data compatibility is assumed.
     * @param data the data to be processed
     */
    @Override
    protected String processData(Instances[] data, String info) throws Exception {

        if (info == null){
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < this.attributePrefixes.length; ++j) {
                sb.append(this.filterAttributePrefix(data, this.attributePrefixes[j]));                
            }
            return sb.toString();
        }
        else {
            String [] attribInfos = info.split("\\n");

            for (String attribInfo : attribInfos){
                String [] nameVal = attribInfo.split(":", 2);
                Set<String> allowedVals = this.findAllowedValues(data, nameVal[0], nameVal[1]);
                this.filterAttribute(nameVal[0], data, allowedVals);
            }
            return info;
        }
    }


    /**
     * Given the input info line, this finds out the valid attribute values.
     *
     * @param data the data to be processed (equal headers assumed)
     * @param attrName the name of the attribute to be processed
     * @param values the string list of allowed attribute value labels (quoted)
     * @return a set of allowed values
     */
    private Set<String> findAllowedValues(Instances [] data, String attrName, String values) throws TaskException{

        Attribute attr = data[0].attribute(attrName);
        if (attr == null || !attr.isNominal()){
            throw new TaskException(TaskException.ERR_INVALID_DATA, this.id, "Missing attribute " + attrName);
        }

        // parse the values
        Vector<String> valList = StringUtils.parseCSV(values);
        HashSet<String> valSet = new HashSet<String>(valList.size());

        for (int i = 0; i < valList.size(); ++i){
            valSet.add(StringUtils.unquote(valList.get(i).trim()));
        }
        return valSet;
    }

}
