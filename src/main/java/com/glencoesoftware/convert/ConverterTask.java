package com.glencoesoftware.convert;

import com.glencoesoftware.bioformats2raw.Converter;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import picocli.CommandLine;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class ConverterTask extends Task<Integer> {
    private final List<String> args;
    private final ListView<IOPackage> inputFileList;
    private final TextArea logBox;
    private final TextField statusBox;
    private final PrimaryController parent;
    public boolean interrupted;

    public ConverterTask(List<String> args, PrimaryController parent) {
        this.parent = parent;
        this.args = args;
        this.inputFileList = parent.inputFileList;
        this.statusBox = parent.statusBox;
        this.logBox = parent.logBox;
        this.interrupted = false;
    }

    @Override
    protected Integer call() {
        CommandLine runner = new CommandLine(new Converter());
        PrintWriter writer = new PrintWriter(new StringWriter());
        runner.setOut(writer);
        runner.setErr(writer);
        int count = 0;
        for (IOPackage job : inputFileList.getItems()) {
            File in = job.fileIn;
            File out = job.fileOut;
            if (this.interrupted || job.status.equals("success") || job.status.equals("error")) {
                continue;
            }

            job.status = "running";
            Platform.runLater(() -> {
                statusBox.setText("Working on " + in.getName());
                logBox.appendText("Working on " + in.getName() + "\n");
                inputFileList.refresh();
            });

            // Construct args list
            ArrayList<String> params = new ArrayList<>(args);
            params.add(0, out.getAbsolutePath());
            params.add(0, in.getAbsolutePath());
            String[] fullParams = params.toArray(new String[args.size()]);
            Platform.runLater(() -> logBox.appendText("Executing with args " + Arrays.toString(fullParams) + "\n"));

            runner.setExecutionExceptionHandler(new RunnerExecutionExceptionHandler());
            runner.setParameterExceptionHandler(new RunnerParameterExceptionHandler());
            int result = runner.execute(fullParams);

            Platform.runLater(() -> {
                if (this.interrupted && result != 0) {
                    job.status = "fail";
                    logBox.appendText("User aborted job: " + out.getName() + "\n\n");
                } else if (result == 0 && out.exists()) {
                    job.status = "success";
                    logBox.appendText("Successfully created: " + out.getName() + "\n\n");
                } else if (result == 0) {
                    job.status = "noOutput";
                    logBox.appendText("Job completed but no output detected: " + out.getName() + "\n\n");
                } else if (result == 9) {
                    job.status = "fail";
                    logBox.appendText("Input arguments were invalid for: " + out.getName() + "\n\n");
                } else {
                    job.status = "fail";
                    logBox.appendText("Failed with Exit Code " + result +  " : " + out.getName() + "\n\n");
                }
                inputFileList.refresh();
            });
            if (result == 0) { count++; }
        }
        String finalStatus = String.format("Completed conversion of %s files.", count);
        Platform.runLater(() -> {
            statusBox.setText(finalStatus);
            logBox.appendText(finalStatus + "\n");
            parent.runCompleted();
        });
        return 0;
    }

    private class RunnerExecutionExceptionHandler implements CommandLine.IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(Exception ex, CommandLine commandLine, CommandLine.ParseResult parsed)  {
            Platform.runLater(() -> logBox.appendText(ex + "\n"));
            return commandLine.getExecutionResult();
        }
    }

    private class RunnerParameterExceptionHandler implements CommandLine.IParameterExceptionHandler {
        @Override
        public int handleParseException(CommandLine.ParameterException ex, String[] args) {
            Platform.runLater(() -> logBox.appendText(ex + "\n"));
            return 9;
        }
    }
}