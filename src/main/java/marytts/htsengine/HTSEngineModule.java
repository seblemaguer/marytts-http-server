package marytts.htsengine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import marytts.MaryException;
import marytts.config.MaryConfiguration;
import marytts.data.Sequence;
import marytts.data.SupportedSequenceType;
import marytts.data.Utterance;
import marytts.data.item.acoustic.AudioItem;
import marytts.data.item.phonology.Phoneme;
import marytts.data.item.phonology.Phone;
import marytts.exceptions.MaryConfigurationException;
import marytts.io.serializer.DefaultHTSLabelSerializer;
import marytts.io.serializer.Serializer;
import marytts.modules.MaryModule;
import org.apache.logging.log4j.core.Appender;

/**
 *
 *
 * @author <a href="mailto:slemaguer@coli.uni-saarland.de">Sébastien Le Maguer</a>
 */
public class HTSEngineModule extends MaryModule {

    public HTSEngineModule() {
        super("synthesizer");
    }

    public void checkStartup() throws MaryConfigurationException
    {
        // FIXME: todo
    }

    /**
     *  Check if the input contains all the information needed to be
     *  processed by the module.
     *
     *  @param utt the input utterance
     *  @throws MaryException which indicates what is missing if something is missing
     */
    public void checkInput(Utterance utt) throws MaryException
    {
        if (!utt.hasSequence(SupportedSequenceType.FEATURES)) {
            throw new MaryException("Feature sequence is missing", null);
        }
    }

    public Utterance process(Utterance utt, MaryConfiguration runtime_configuration)
    throws MaryException
    {

        try {
            // Write the label
            File tmp_label = File.createTempFile("htsengine", ".lab");
            BufferedWriter bw = new BufferedWriter(new FileWriter(tmp_label));
            bw.write((new DefaultHTSLabelSerializer()).export(utt).toString());
            bw.close();

            // Prepare the synthesis
            File tmp_dur_lab = File.createTempFile("htsengine", ".lab_with_dur");
            File tmp_wav = File.createTempFile("htsengine", ".wav");
            String command = String.format("hts_engine -m %s -ow %s -od %s %s",
                                           "/home/slemaguer/Downloads/htsengine/arctic_slt.htsvoice",
                                           tmp_wav.toString(),
                                           tmp_dur_lab.toString(),
                                           tmp_label.toString());

            // Execute synthesis
            execCommand(command);

            // Update phoneme sequence
            Sequence<Phoneme> seq_ph = (Sequence<Phoneme>) utt.getSequence(SupportedSequenceType.PHONE);
            ArrayList<String> lines = new ArrayList<String>(Files.readAllLines(tmp_dur_lab.toPath()));
            if (lines.size() != seq_ph.size()) {
                throw new MaryException("the HTS engine duration label number of lines doesn't correspond to the given number of segments");
            }
            for (int i=0; i<lines.size(); i++) {
                String[] line_elts = lines.get(i).split(" ");
                double start = Integer.parseInt(line_elts[0]) / 10000.0;
                double end = Integer.parseInt(line_elts[1]) / 10000.0;
                seq_ph.set(i, new Phone(seq_ph.get(i), start, end - start));
            }

            // Add the signal to the utterance
            AudioItem audio_it = new AudioItem(tmp_wav.toString());
            Sequence<AudioItem> seq_aud = new Sequence<AudioItem>();
            seq_aud.add(audio_it);
            utt.addSequence(SupportedSequenceType.AUDIO, seq_aud);

            // clean
            tmp_dur_lab.delete();
            tmp_label.delete();
            tmp_wav.delete();

            return utt;
        } catch (Exception ex) {
            throw new MaryException("Problem during synthesis", ex);
        }
    }


    protected void setDescription()
    {
        this.description = "HMM synthesizer module using hts engine";
    }



    public void execCommand(String command) throws IOException, InterruptedException, MaryException  {

        // Run the command
        final Process process = Runtime.getRuntime().exec(command);

        // Consommation de la sortie standard de l'application externe dans un Thread separe
        new Thread() {
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line = "";
                    try {
                        while ((line = reader.readLine()) != null) {
                            // Traitement du flux de sortie de l'application si besoin est
                        }
                    } finally {
                        reader.close();
                    }
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        } .start();

        // Consommation de la sortie d'erreur de l'application externe dans un Thread separe
        new Thread() {
            public void run() {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    String line = "";
                    try {
                        while ((line = reader.readLine()) != null) {
                            System.out.println("line = " + line);
                        }
                    } finally {
                        reader.close();
                    }
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        } .start();


        process.waitFor();

    }
}


/* HMMSynthesizer.java ends here */
