package es.us.isa.httpmutator.body.value.string0.operator;

import java.util.ArrayList;
import java.util.List;

import es.us.isa.httpmutator.AbstractOperator;
import es.us.isa.httpmutator.util.OperatorNames;
import static es.us.isa.httpmutator.util.PropertyManager.readProperty;

/**
 * Operator that mutates a string by adding special characters like "/", "*", and ",".
 *
 * @author Ana Belén Sánchez
 */
public class StringAddSpecialCharactersMutationOperator extends AbstractOperator {

    public StringAddSpecialCharactersMutationOperator() {
    	 super();
         weight = Float.parseFloat(readProperty("operator.value.string.weight." + OperatorNames.ADD_SPECIAL_CHARACTERS));
     }

     @Override
     protected Object doMutate(Object stringObject) {
         String string = (String)stringObject;
         StringBuilder sb = new StringBuilder(string);
         if(string.length()>0) {
         
         	List<String> specialCharacters = new ArrayList<String>();
         	specialCharacters.add("/"); specialCharacters.add("*"); specialCharacters.add(","); 
         	specialCharacters.add("´"); specialCharacters.add("´*");specialCharacters.add("/*"); 
         	specialCharacters.add("/,"); specialCharacters.add("*,"); specialCharacters.add("´/"); 
         	specialCharacters.add("´,");
         	
         	int charPosition = rand1.nextInt(0, string.length()-1);
         	int posRandomCharacter = rand1.nextInt(0, specialCharacters.size()-1);

         	//Inserting special character
         	sb.insert(charPosition, specialCharacters.get(posRandomCharacter));
     
         }

         return sb.toString();
     }
 } 