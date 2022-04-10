package com.myorg;

import software.amazon.awscdk.App;

public final class StudentErpRefactorApp {
    public static void main(final String[] args) {
        App app = new App();

        new StudentErpRefactorStack(app, "StudentErpRefactorStack");

        app.synth();
    }
}
