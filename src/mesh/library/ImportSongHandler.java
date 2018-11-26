package mesh.library;

import connect.Song;
import javafx.concurrent.Task;
import utils.Utils;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ImportSongHandler extends Task<Song> {

    private File file;

    private String title;

    private String artist;

    private String album;

    private MeshLibrary library;

    public ImportSongHandler(File file, String title, String artist, String album, MeshLibrary library) {
        this.file = file;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.library = library;
    }

    @Override
    protected Song call() throws Exception {
        long duration = 0;

        System.out.println("[ImportSongHandler][run] Importing \"" + this.title + "\" from file: " + this.file.getPath());

        long totalWork = this.file.length();
        long nonMeasurableTaskValue = totalWork / 10;
        totalWork += (nonMeasurableTaskValue * 2);
        long workDone = 0;
        this.updateProgress(workDone, totalWork);

        try {
            System.out.println("[ImportSongHandler][run] Getting song metadata...");
            AudioInputStream ain = AudioSystem.getAudioInputStream(this.file);
            duration = Math.round(ain.getFrameLength() / ain.getFormat().getFrameRate());
            ain.close();
            System.out.println("[ImportSongHandler][run] Song duration=" + duration);
            workDone += nonMeasurableTaskValue;
            this.updateProgress(workDone, totalWork);

        } catch (UnsupportedAudioFileException | IOException e) {
            System.err.println("[ImportSongHandler][run] Unable to get duration of audio file");
            e.printStackTrace();
        }

        String songFileName = Utils.hash(String.join(".", new String[]{this.artist, this.album, this.title}), "MD5");

        System.out.println("[ImportSongHandler][run] songFileName=\"" + songFileName + "\"");

        OutputStream out = null;
        InputStream in = null;
        try {
            System.out.println("[ImportSongHandler][run] Opening DFS output stream");
            Future<OutputStream> fout = library.dfs.writeFile(songFileName, MeshLibrary.SONG_FILE_REPLICAS);
            out = fout.get(10, TimeUnit.SECONDS);

            System.out.println("[ImportSongHandler][run] Opening source file");
            in = new BufferedInputStream(new FileInputStream(this.file));

            System.out.println("[ImportSongHandler][run] Copying file to DFS...");

            byte[] trx = new byte[1024 * 8];
            long acc = 0;
            int trxd = 0;
            while ((trxd = in.read(trx, 0, trx.length)) != -1) {
                out.write(trx, 0, trxd);
                this.updateProgress(workDone + acc, totalWork);
                //System.out.println("[ImportSongHandler][run] " + trxd + " bytes written to DFS");
            }

            workDone += acc;

            in.close();
            out.close();

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            System.err.println("[ImportSongHandler][run] Exception while trying to import song");
            e.printStackTrace();
            throw e;

        } catch (IOException e) {
            System.err.println("[ImportSongHandler][run] IOException while uploading song to DFS");
            e.printStackTrace();

            try { if (out != null) out.close(); } catch (IOException e1) {}
            try { if (in != null) out.close(); } catch (IOException e1) {}

            throw e;
        }

        // append song metadata to inverted index file
        StringBuilder builder = new StringBuilder();
        builder.append(this.artist); builder.append(';');
        builder.append(this.album); builder.append(';');
        builder.append(this.title); builder.append(';');
        builder.append(duration); builder.append(';');
        builder.append(songFileName); builder.append('\n');
        builder.trimToSize();

        this.library.dfs.appendFile(MeshLibrary.INDEX_FILE_NAME, builder.toString().getBytes(), MeshLibrary.INDEX_FILE_REPLICAS);

        workDone += nonMeasurableTaskValue;
        this.updateProgress(workDone, totalWork);

        System.out.println("[ImportSongHandler][run] Imported song \"" + this.title + "\" successfully!");
        MeshClientSong song = new MeshClientSong(this.title, this.artist, this.album, duration, songFileName, this.library);
        this.library.addSong(song);

        this.updateProgress(totalWork, totalWork);

        return song;
    }
}