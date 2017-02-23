package com.example.mayo.rfidpairingtest;

import com.zebra.rfid.api3.InvalidUsageException;
import com.zebra.rfid.api3.OperationFailureException;
import com.zebra.rfid.api3.RFIDReader;
import com.zebra.rfid.api3.RFIDResults;
import com.zebra.rfid.api3.ReaderDevice;
import com.zebra.rfid.api3.Readers;
import com.zebra.rfid.api3.RegionInfo;
import com.zebra.rfid.api3.RegulatoryConfig;

import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;


public class MainActivity extends AppCompatActivity  implements Readers.RFIDReaderEventHandler
{
    private TextView tPairStatus;
    private TextView tDeviceName;

    private Readers readers;
    private ReaderDevice readerDevice;
    private RFIDReader rfidReader;

    private int connectionAttempts;
    private final int MAX_CONNECTION_ATTEMPTS = 5;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tPairStatus = (TextView)findViewById(R.id.status_text);
        tDeviceName = (TextView)findViewById(R.id.device_name_text);

        getCurrentDevices();
    }


    @Override
    protected void onDestroy()
    {
        disconnectReaderInstance();
        readers.deattach(this);
        super.onDestroy();
    }


    @Override
    public void RFIDReaderAppeared(ReaderDevice device)
    {
        if (device == null)
        {
            tPairStatus.setText(R.string.error_device_is_null);
            return;
        }

        updateConnectionStatus(device);
    }


    @Override
    public void RFIDReaderDisappeared(ReaderDevice device)
    {
        tPairStatus.setText(R.string.status_disconnected);
        tDeviceName.setText(R.string.n_a);
    }


    private boolean isBluetoothEnabled()
    {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        return btAdapter != null && btAdapter.isEnabled();
    }


    private void getCurrentDevices()
    {
        if (readers == null)
        {
            readers = new Readers();
            readers.attach(this);
        }

        if (!isBluetoothEnabled())
        {
            Toast.makeText(
                    getApplicationContext(),
                    getResources().getString(R.string.error_bluetooth_disabled),
                    Toast.LENGTH_LONG).show();

            return;
        }

        ArrayList<ReaderDevice> availableRFIDReaderList = readers.GetAvailableRFIDReaderList();
        if (availableRFIDReaderList.size() > 0)
        {
            updateConnectionStatus(availableRFIDReaderList.get(0));
        }
    }


    private void updateConnectionStatus(ReaderDevice device)
    {
        disconnectReaderInstance();
        readerDevice = device;

        tPairStatus.setText(R.string.status_paired);
        tDeviceName.setText(readerDevice.getName());
        connectToRFIDReader(readerDevice.getRFIDReader());
    }


    private void connectToRFIDReader(RFIDReader reader)
    {
        if (connectionAttempts >= MAX_CONNECTION_ATTEMPTS)
        {
            tPairStatus.setText(R.string.error_max_attempts);
            return;
        }
        connectionAttempts++;
        rfidReader = reader;

        try
        {
            rfidReader.connect();
            tPairStatus.setText(R.string.status_connected);
            // Reset connectionIntents
            connectionAttempts = 0;
        }
        catch (InvalidUsageException e)
        {
            tPairStatus.setText(e.getMessage());
            e.printStackTrace();
        }
        catch (OperationFailureException e)
        {
            handleOperationFailureException(e);
        }
    }


    private void handleOperationFailureException(OperationFailureException exception)
    {
        if (exception.getResults() == RFIDResults.RFID_READER_REGION_NOT_CONFIGURED)
        {
            tPairStatus.setText(R.string.error_region);
            try
            {
                // Get and Set regulatory configuration settings
                RegulatoryConfig regulatoryConfig = rfidReader.Config.getRegulatoryConfig();
                RegionInfo regionInfo = rfidReader.ReaderCapabilities.SupportedRegions.getRegionInfo(1);
                regulatoryConfig.setRegion(regionInfo.getRegionCode());
                rfidReader.Config.setRegulatoryConfig(regulatoryConfig);

            }
            catch (Exception e)
            {
                tPairStatus.setText(e.getMessage());
                e.printStackTrace();
            }
        }
        else if (exception.getResults() == RFIDResults.RFID_CONNECTION_PASSWORD_ERROR)
        {
            tPairStatus.setText(R.string.error_incorrect_password);
            String password = askPassword();
            if (password == null)
            {
                // TODO; can I just retrive the current password? -- Not secure at all !
                password = rfidReader.getPassword();
            }

            rfidReader.setPassword(password);
        }
        else if (exception.getResults() == RFIDResults.RFID_BATCHMODE_IN_PROGRESS)
        {
            tPairStatus.setText(R.string.error_batch_mode);
            // TODO, stop batch or wait until is done.. Until it doesn't happen, do not try to reconnect
            return;
        }

        // Try again..
        connectToRFIDReader(rfidReader);
    }


    private void disconnectReaderInstance()
    {
        if (rfidReader != null && rfidReader.isConnected())
        {
            try
            {
                rfidReader.disconnect();
            }
            catch (InvalidUsageException e)
            {
                e.printStackTrace();
            }
            catch (OperationFailureException e)
            {
                e.printStackTrace();
            }
        }
    }


    private String askPassword()
    {
        // Dirty trick; I'm using an array instead of a simple String to use (and modify) a final var.
        // It's final bc is used inside an inner annonymous class
        final String[] m_Text = {null};

        // Set up the input
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle(R.string.password_dlg_title)
                .setMessage(R.string.password_dlg_text)
                .setView(input)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        m_Text[0] = input.getText().toString();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                })
                .show();

        return m_Text[0];
    }
}
