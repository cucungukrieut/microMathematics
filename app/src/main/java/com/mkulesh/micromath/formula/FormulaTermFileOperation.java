/*******************************************************************************
 * microMathematics Plus - Extended visual calculator
 * *****************************************************************************
 * Copyright (C) 2014-2017 Mikhail Kulesh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package com.mkulesh.micromath.formula;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.mkulesh.micromath.fman.FileUtils;
import com.mkulesh.micromath.formula.CalculaterTask.CancelException;
import com.mkulesh.micromath.math.CalculatedValue;
import com.mkulesh.micromath.plus.R;
import com.mkulesh.micromath.widgets.CustomEditText;
import com.mkulesh.micromath.widgets.CustomTextView;

import org.apache.commons.math3.complex.Complex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;

public class FormulaTermFileOperation extends FormulaTerm
{
    /**
     * Supported functions
     */
    public enum FileOperationType
    {
        READ(R.drawable.p_file_read, R.string.math_file_read);

        private final int imageId;
        private final int descriptionId;
        private final String lowerCaseName;

        private FileOperationType(int imageId, int descriptionId)
        {
            this.imageId = imageId;
            this.descriptionId = descriptionId;
            this.lowerCaseName = name().toLowerCase(Locale.ENGLISH);
        }

        public int getImageId()
        {
            return imageId;
        }

        public int getDescriptionId()
        {
            return descriptionId;
        }

        public String getLowerCaseName()
        {
            return lowerCaseName;
        }
    }

    public static FileOperationType getFileOperationType(Context context, String s)
    {
        String fName = null;
        final String startBracket = context.getResources().getString(R.string.formula_function_start_bracket);
        if (s.contains(startBracket))
        {
            fName = s.substring(0, s.indexOf(startBracket)).trim();
        }
        for (FileOperationType f : FileOperationType.values())
        {
            if (s.equals(f.getLowerCaseName()))
            {
                return f;
            }
            if (fName != null && fName.equals(f.getLowerCaseName()))
            {
                return f;
            }
        }
        return null;
    }

    public static boolean containsTrigger(Context context, String s)
    {
        return s.contains(context.getResources().getString(R.string.formula_function_start_bracket));
    }

    /**
     * Private attributes
     */
    private FileOperationType fileOperationType = null;
    private TermField fileName = null;
    private final ArrayList<ArrayList<String>> fileBuffer = new ArrayList<>();

    /*********************************************************
     * Constructors
     *********************************************************/

    public FormulaTermFileOperation(TermField owner, LinearLayout layout, String s, int idx) throws Exception
    {
        super(owner.getFormulaRoot(), layout, owner.termDepth);
        setParentField(owner);
        onCreate(s, idx);
    }

    /*********************************************************
     * GUI constructors to avoid lint warning
     *********************************************************/

    public FormulaTermFileOperation(Context context)
    {
        super();
    }

    public FormulaTermFileOperation(Context context, AttributeSet attrs)
    {
        super();
    }

    /*********************************************************
     * Re-implementation for methods for FormulaBase and FormulaTerm superclass's
     *********************************************************/

    @Override
    public CalculatedValue.ValueType getValue(CalculaterTask thread, CalculatedValue outValue) throws CancelException
    {
        if (getFormulaRoot() instanceof Equation)
        {
            Equation eq = (Equation) getFormulaRoot();
            final int argNumber = eq.getArguments() != null ? eq.getArguments().size() : 0;
            String strValue = null;
            if (argNumber == 1 || argNumber == 2)
            {
                final int a0 = eq.getArgumentValue(0).getInteger();
                if (a0 < fileBuffer.size())
                {
                    final ArrayList<String> line = fileBuffer.get(a0);
                    final int a1 = (argNumber == 1) ? 0 : eq.getArgumentValue(1).getInteger();
                    if (a1 < line.size())
                    {
                        strValue = line.get(a1);
                    }
                }
            }
            if (strValue != null)
            {
                try
                {
                    return outValue.setValue(Double.parseDouble(strValue));
                }
                catch (Exception ex)
                {
                    // nothing to do: we will try to convert it to complex
                }
                Complex cmplValue = TermParser.complexValueOf(strValue);
                if (cmplValue != null)
                {
                    if (cmplValue.getImaginary() != 0.0)
                    {
                        return outValue.setComplexValue(cmplValue.getReal(), cmplValue.getImaginary());
                    }
                    else
                    {
                        return outValue.setValue(cmplValue.getReal());
                    }
                }
            }
            return outValue.invalidate(CalculatedValue.ErrorType.NOT_A_NUMBER);
        }
        return outValue.invalidate(CalculatedValue.ErrorType.TERM_NOT_READY);
    }

    @Override
    public DifferentiableType isDifferentiable(String var)
    {
        return DifferentiableType.NONE;
    }

    @Override
    public CalculatedValue.ValueType getDerivativeValue(String var, CalculaterTask thread, CalculatedValue outValue)
            throws CancelException
    {
        return outValue.invalidate(CalculatedValue.ErrorType.TERM_NOT_READY);
    }

    @Override
    public TermType getTermType()
    {
        return TermType.FILE_OPERATION;
    }

    @Override
    public String getTermCode()
    {
        return getFileOperationType().getLowerCaseName();
    }

    @Override
    public boolean isContentValid(ValidationPassType type)
    {
        String errorMsg = null;
        switch (type)
        {
        case VALIDATE_SINGLE_FORMULA:
            fileBuffer.clear();
            final InputStream fileStream = openFileStream(fileName.getText());
            if (fileStream == null)
            {
                errorMsg = String.format(getContext().getResources().getString(R.string.error_file_read),
                        fileName.getText());
            }
            else
            {
                FileUtils.closeStream(fileStream);
            }
            break;
        case VALIDATE_LINKS:
            break;
        }

        if (parentField != null && functionMainLayout != null)
        {
            parentField.setError(errorMsg, TermField.ErrorNotification.PARENT_LAYOUT, functionMainLayout);
        }

        return errorMsg == null;
    }

    @Override
    protected CustomTextView initializeSymbol(CustomTextView v)
    {
        final Resources res = getContext().getResources();
        if (v.getText() != null)
        {
            String t = v.getText().toString();
            if (t.equals(res.getString(R.string.formula_operator_key)))
            {
                v.prepare(CustomTextView.SymbolType.TEXT,
                        getFormulaRoot().getFormulaList().getActivity(), this);
                v.setText(getTermCode());
            }
            else if (t.equals(res.getString(R.string.formula_left_bracket_key)))
            {
                v.prepare(CustomTextView.SymbolType.LEFT_BRACKET,
                        getFormulaRoot().getFormulaList().getActivity(), this);
                v.setText("."); // this text defines view width/height
            }
            else if (t.equals(res.getString(R.string.formula_right_bracket_key)))
            {
                v.prepare(CustomTextView.SymbolType.RIGHT_BRACKET,
                        getFormulaRoot().getFormulaList().getActivity(), this);
                v.setText("."); // this text defines view width/height
            }
        }
        return v;
    }

    @Override
    protected CustomEditText initializeTerm(CustomEditText v, LinearLayout l)
    {
        if (v.getText() != null)
        {
            final String t = v.getText().toString();
            if (t.equals(getContext().getResources().getString(R.string.formula_arg_term_key)))
            {
                fileName = addTerm(getFormulaRoot(), l, -1, v, this, 0);
                fileName.bracketsType = TermField.BracketsType.NEVER;
            }
        }
        return v;
    }

    /*********************************************************
     * FormulaTermFileOperation-specific methods
     *********************************************************/

    /**
     * Procedure creates the formula layout
     */
    private void onCreate(String s, int idx) throws Exception
    {
        if (idx < 0 || idx > layout.getChildCount())
        {
            throw new Exception("cannot create FormulaTermFileOperation for invalid insertion index " + idx);
        }
        fileOperationType = getFileOperationType(getContext(), s);
        if (fileOperationType == null)
        {
            throw new Exception("cannot create FormulaTermFileOperation for unknown function");
        }
        inflateElements(R.layout.formula_file_operation, true);
        initializeElements(idx);
        initializeMainLayout();
        if (fileName == null)
        {
            throw new Exception("cannot initialize function terms");
        }
    }

    /**
     * Returns function type
     */
    public FileOperationType getFileOperationType()
    {
        return fileOperationType;
    }

    private InputStream openFileStream(final String name)
    {
        if (name == null || name.length() == 0)
        {
            return null;
        }

        Uri imageUri = null;
        if (name.contains(FileUtils.ASSET_RESOURCE_PREFIX))
        {
            imageUri = Uri.parse(name);
        }
        else
        {
            imageUri = FileUtils.catUri(getContext(), getFormulaList().getParentDirectory(), name);
        }

        if (imageUri == null)
        {
            return null;
        }

        return FileUtils.getInputStream(getContext(), imageUri, false);
    }

    public void prepareFileOperation()
    {
        fileBuffer.clear();
        final InputStream fileStream = openFileStream(fileName.getText());
        if (fileStream == null)
        {
            return;
        }

        try
        {
            BufferedReader r = new BufferedReader(new InputStreamReader(fileStream));
            String line;
            while ((line = r.readLine()) != null)
            {
                // ignore empty strings
                final String trimmedString = line.trim();
                if (trimmedString.isEmpty())
                {
                    continue;
                }
                final String[] tokens = trimmedString.split("\\s+");
                final ArrayList<String> tokenList = new ArrayList<>(tokens.length);
                for (String s : tokens)
                {
                    final String s1 = s.trim();
                    if (!s1.isEmpty())
                    {
                        tokenList.add(s1);
                    }
                }
                fileBuffer.add(tokenList);
            }
        }
        catch (IOException e)
        {
            // nothing to do
        }

        FileUtils.closeStream(fileStream);
    }

    public void finishFileOperation()
    {
        fileBuffer.clear();
    }

    /**
     * Add palette buttons for this term
     */
    public static void addToPalette(Context context, LinearLayout paletteLayout,
                                    PaletteButton.Category[] categories)
    {
        for (int i = 0; i < FileOperationType.values().length; i++)
        {
            final FileOperationType t = FileOperationType.values()[i];
            if (t.getImageId() != Palette.NO_BUTTON)
            {
                PaletteButton p = new PaletteButton(context,
                        Palette.NO_BUTTON, t.getImageId(), t.getDescriptionId(),
                        t.getLowerCaseName());
                paletteLayout.addView(p);
                p.setCategories(categories);
            }
        }
    }
}
