/**
 * @license Copyright 2014-2018 Günter Fuchs (gfuchs@acousticmicroscopy.com)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * Modifications by Peter Froud, June 2018
 */
package xyz.froud.jvisa;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.ptr.NativeLongByReference;
import xyz.froud.jvisa.eventhandling.JVisaEventHandler;
import xyz.froud.jvisa.eventhandling.JVisaEventType;

import java.nio.ByteBuffer;

/**
 * Represents a Visa instrument. This is a wrapper around the native C instrument handle.
 * <p>
 * To use this class, call {@link JVisaResourceManager#openInstrument} from a JVisaResourceManager instance.
 *
 * @author Günter Fuchs (gfuchs@acousticmicroscopy.com)
 * @author Peter Froud
 */
public class JVisaInstrument implements AutoCloseable {

    private final static int DEFAULT_BUFFER_SIZE = 1024;

    private final NativeLong INSTRUMENT_HANDLE;
    private final JVisaResourceManager RESOURCE_MANAGER;
    private final JVisaLibrary VISA_LIBRARY;
    public final String RESOURCE_NAME;

    public JVisaInstrument(JVisaResourceManager resourceManager, NativeLongByReference instrumentHandle, String resourceName) {
        RESOURCE_MANAGER = resourceManager;
        VISA_LIBRARY = resourceManager.VISA_LIBRARY;
        INSTRUMENT_HANDLE = instrumentHandle.getValue();
        RESOURCE_NAME = resourceName;
    }

    /**
     * Sends a command and receives its response. It insists in receiving at least a given number of bytes.
     *
     * @param command string to send
     * @param bufferSize size of buffer to allocate. The size can be set smaller since it gets allocated with readCount.
     * @return response from instrument as a String
     * @throws JVisaException if the write fails or the read fails
     */
    public String sendAndReceiveString(String command, int bufferSize) throws JVisaException {
        write(command);
        return readString(bufferSize);
    }

    /**
     * Sends a command and receives its response. It receives as many bytes as the instrument is sending. (That is probably wrong, it can receive maximum DEFAULT_BUFFER_SIZE bytes)
     *
     * @param command string to send
     * @return response from instrument as a String
     * @throws JVisaException if the write fails or the read fails
     */
    public String sendAndReceiveString(String command) throws JVisaException {
        write(command);
        return readString(DEFAULT_BUFFER_SIZE);
    }

    /**
     * Sends a command and receives its response. It insists in receiving at least a given number of bytes.
     *
     * @param command string to send
     * @param bufferSize size of buffer to allocate. The size can be set smaller since it gets allocated with readCount.
     * @return response from instrument as a ByteBuffer
     * @throws JVisaException if the write fails or the read fails
     */
    public ByteBuffer sendAndReceiveBytes(String command, int bufferSize) throws JVisaException {
        write(command);
        return readBytes(bufferSize);
    }

    /**
     * Sends a command and receives its response. It receives as many bytes as the instrument is sending.
     *
     * @param command string to send
     * @return response from instrument as a ByteBuffer
     * @throws JVisaException if the write fails or the read fails
     */
    public ByteBuffer sendAndReceiveBytes(String command) throws JVisaException {
        write(command);
        return readBytes(DEFAULT_BUFFER_SIZE);
    }

    /**
     * Sends a command to the instrument.
     *
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/viwrite.html">viWrite</a>
     *
     * @param command the command to send
     * @throws JVisaException if the write fails
     */
    public void write(String command) throws JVisaException {
        final ByteBuffer buffer = JVisaUtils.stringToByteBuffer(command);
        final int commandLength = command.length();

        final NativeLongByReference returnCount = new NativeLongByReference();
        final NativeLong visaStatus = VISA_LIBRARY.viWrite(INSTRUMENT_HANDLE, buffer, new NativeLong(commandLength), returnCount);

        JVisaUtils.throwForStatus(RESOURCE_MANAGER, visaStatus, "viWrite");

        final long count = returnCount.getValue().longValue();
        if (count != commandLength) {
            throw new JVisaException(String.format("Could only write %d instead of %d bytes.",
                    count, commandLength));
        }
    }

    /**
     * Reads data from the instrument, e.g. a command response or data.
     *
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/viread.html">viRead</a>
     *
     * @param bufferSize size of response buffer in bytes
     * @return response from instrument as bytes
     * @throws JVisaException if the read fails
     */
    protected ByteBuffer readBytes(int bufferSize) throws JVisaException {
        final NativeLongByReference readCountNative = new NativeLongByReference();
        final ByteBuffer responseBuf = ByteBuffer.allocate(bufferSize);

        final NativeLong visaStatus = VISA_LIBRARY.viRead(INSTRUMENT_HANDLE, responseBuf, new NativeLong(bufferSize), readCountNative);
        JVisaUtils.throwForStatus(RESOURCE_MANAGER, visaStatus, "viRead");

        final long readCount = readCountNative.getValue().longValue();
        if (readCount < 1) {
            throw new JVisaException("read zero bytes from instrument");
        }

        return responseBuf;
    }

    /**
     * Reads a string from the instrument, e.g. a command response.
     *
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/viread.html">viRead</a>
     *
     * @param bufferSize size of response buffer in bytes
     * @return response from the instrument as a String
     * @throws JVisaException if the read fails
     */
    public String readString(int bufferSize) throws JVisaException {
        final NativeLongByReference readCountNative = new NativeLongByReference();
        final ByteBuffer responseBuf = ByteBuffer.allocate(bufferSize);

        final NativeLong visaStatus = VISA_LIBRARY.viRead(INSTRUMENT_HANDLE, responseBuf, new NativeLong(bufferSize), readCountNative);
        JVisaUtils.throwForStatus(RESOURCE_MANAGER, visaStatus, "viRead");

        final long readCount = readCountNative.getValue().longValue();
        if (readCount < 1) {
            throw new JVisaException("read zero bytes from instrument");
        }

        return new String(responseBuf.array(), 0, (int) readCount).trim();
    }

    /**
     * reads a string from the instrument, usually a command response.
     *
     * @return status of the operation
     * @throws JVisaException if the read fails
     */
    public String readString() throws JVisaException {
        return readString(DEFAULT_BUFFER_SIZE);
    }

    /**
     * Clears the device input and output buffers. The corresponding VISA function is not implemented in the libreVisa library.
     *
     *  @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/viclear.html">viClear</a>
     *
     * @throws JVisaException if the clear operation failed
     */
    public void clear() throws JVisaException {
        final NativeLong visaStatus = VISA_LIBRARY.viClear(INSTRUMENT_HANDLE);
        JVisaUtils.throwForStatus(RESOURCE_MANAGER, visaStatus, "viClear");
    }

    /**
     * Closes an instrument session.
     *
     *  @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/viclosehtml">viClose</a>
     *
     * @throws JVisaException if the instrument couldn't be closed
     */
    @Override
    public void close() throws JVisaException {
        final NativeLong visaStatus = VISA_LIBRARY.viClose(INSTRUMENT_HANDLE);
        JVisaUtils.throwForStatus(RESOURCE_MANAGER, visaStatus, "viClose");
    }

    /**
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/vi_attr_tmo_value.html">VI_ATTR_TMO_VALUE</a>
     *
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/visetattribute.html">viSetAttribute</a>
     */
    public void setTimeout(int timeoutMilliseconds) throws JVisaException {
        final NativeLong visaStatus = VISA_LIBRARY.viSetAttribute(INSTRUMENT_HANDLE,
                new NativeLong(JVisaLibrary.VI_ATTR_TMO_VALUE),
                new NativeLong(timeoutMilliseconds)
        );

        JVisaUtils.throwForStatus(RESOURCE_MANAGER, visaStatus, "viSetAttribute");
    }

    /**
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/vigetattribute.html">viGetAttribute</a>
     */
    public String getAttribute(int attr) throws JVisaException {
        final Memory mem = new Memory(256);

        final NativeLong visaStatus = VISA_LIBRARY.viGetAttribute(INSTRUMENT_HANDLE, new NativeLong(attr), mem);
        JVisaUtils.throwForStatus(RESOURCE_MANAGER, visaStatus, "viGetAttribute");

        // apparently we can't dispose or free or finalize a Memory, just need to let JVM call finalize()
        return mem.getString(0, "UTF-8");
    }

    /**
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/vi_attr_manf_name.html">VI_ATTR_MANF_NAME</a>
     */
    public String getManufacturerName() throws JVisaException {
        return getAttribute(JVisaLibrary.VI_ATTR_MANF_NAME);
    }

    /**
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/vi_attr_model_name.html">VI_ATTR_MODEL_NAME</a>
     */
    public String getModelName() throws JVisaException {
        return getAttribute(JVisaLibrary.VI_ATTR_MODEL_NAME);
    }

    /**
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/vi_attr_usb_serial_num.html">VI_ATTR_SERIAL_NUM</a>
     */
    public String getUsbSerialNumber() throws JVisaException {
        return getAttribute(JVisaLibrary.VI_ATTR_USB_SERIAL_NUM);
    }

    /**
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/viinstallhandler.html">viInstallHandler</a>
     */
    public void addEventHandler(JVisaEventHandler handle) throws JVisaException {
        final NativeLong visaStatus = VISA_LIBRARY.viInstallHandler(INSTRUMENT_HANDLE,
                new NativeLong(handle.EVENT_TYPE.VALUE),
                handle.CALLBACK,
                handle.USER_DATA
        );
        JVisaUtils.throwForStatus(RESOURCE_MANAGER, visaStatus, "viInstallHandler");
    }

    /**
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/viuninstallhandler.html">viUninstallHandler</a>
     */
    public void removeEventHandler(JVisaEventHandler handle) throws JVisaException {
        final NativeLong statusUninstall = VISA_LIBRARY.viUninstallHandler(INSTRUMENT_HANDLE,
                new NativeLong(handle.EVENT_TYPE.VALUE),
                handle.CALLBACK,
                handle.USER_DATA
        );
        JVisaUtils.throwForStatus(RESOURCE_MANAGER, statusUninstall, "viUninstallHandler");
    }

    /**
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/vienableevent.html">viEnableEvent</a>
     */
    public void enableEvent(JVisaEventType eventType) throws JVisaException {

        final NativeLong statusEnableEvent = VISA_LIBRARY.viEnableEvent(
                INSTRUMENT_HANDLE,
                new NativeLong(eventType.VALUE),
                (short) JVisaLibrary.VI_HNDLR, //mechanism
                new NativeLong(0) //context
        );
        JVisaUtils.throwForStatus(RESOURCE_MANAGER, statusEnableEvent, "viEnableEvent");
    }

    /**
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/vidisableevent.html">viDisableEvent</a>
     */
    public void disableEvent(JVisaEventType eventType) throws JVisaException {

        final NativeLong statusEnableEvent = VISA_LIBRARY.viDisableEvent(
                INSTRUMENT_HANDLE,
                new NativeLong(eventType.VALUE),
                (short) JVisaLibrary.VI_HNDLR //mechanism
        );
        JVisaUtils.throwForStatus(RESOURCE_MANAGER, statusEnableEvent, "viDisableEvent");
    }

    /**
     * @see <a href="https://www.ni.com/docs/en-US/bundle/ni-visa/page/ni-visa/vidiscardevents.html">viDiscardEvents</a>
     */
    public void discardEvents(JVisaEventType eventType) throws JVisaException {
        final NativeLong status = VISA_LIBRARY.viDiscardEvents(
                INSTRUMENT_HANDLE,
                new NativeLong(eventType.VALUE),
                (short) JVisaLibrary.VI_ALL_MECH //mechanism
        );
        JVisaUtils.throwForStatus(RESOURCE_MANAGER, status, "viDiscardEvents");
    }

}
