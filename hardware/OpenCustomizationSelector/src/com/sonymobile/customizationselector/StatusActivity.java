package com.sonymobile.customizationselector;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class StatusActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);

        TextView reportText = findViewById(R.id.m_report_text);
        TextView statusText = findViewById(R.id.m_status_text);

        File reportFile = new File(ModemSwitcher.MODEM_REPORT_FILE);
        File statusFile = new File(ModemSwitcher.MODEM_STATUS_FILE);

        reportText.setText(reportFile.exists() ? readFile(reportFile) : getString(R.string.file_not_found));
        statusText.setText(statusFile.exists() ? readFile(statusFile) : getString(R.string.file_not_found));
    }

    private String readFile(File file) {
        StringBuilder data = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            try {
                String line;
                while ((line = reader.readLine()) != null)
                    data.append(line).append("\n");
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return data.toString();
    }
}
