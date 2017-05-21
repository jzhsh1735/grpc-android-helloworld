package com.myexample.helloworld;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import grpc.myexample.hello.HelloRequest;
import grpc.myexample.hello.HelloResponse;
import grpc.myexample.hello.HelloServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String GREETING = "HelloServer!";
    private static final int COUNT = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    public void hello(View view) {
        new HelloTask(new SayHello()).execute();
        new HelloTask(new LotsOfReplies()).execute();
        new HelloTask(new LotsOfGreetings()).execute();
        new HelloTask(new BidiHello()).execute();
    }

    private interface HelloRunnable {
        List<HelloResponse> run(HelloServiceGrpc.HelloServiceBlockingStub blockingStub,
                                HelloServiceGrpc.HelloServiceStub asyncStub);
    }

    private class SayHello implements HelloRunnable {
        @Override
        public List<HelloResponse> run(
                HelloServiceGrpc.HelloServiceBlockingStub blockingStub,
                HelloServiceGrpc.HelloServiceStub asyncStub) {
            List<HelloResponse> result = new ArrayList<>();
            try {
                HelloRequest request = HelloRequest.newBuilder().setGreeting(GREETING).build();
                HelloResponse response = blockingStub.sayHello(request);
                result.add(response);
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
            return result;
        }
    }

    private class LotsOfReplies implements HelloRunnable {
        @Override
        public List<HelloResponse> run(
                HelloServiceGrpc.HelloServiceBlockingStub blockingStub,
                HelloServiceGrpc.HelloServiceStub asyncStub) {
            List<HelloResponse> result = new ArrayList<>();
            try {
                HelloRequest request = HelloRequest.newBuilder().setGreeting(GREETING).build();
                Iterator<HelloResponse> stream = blockingStub.lotsOfReplies(request);
                while (stream.hasNext()) {
                    HelloResponse response = stream.next();
                    result.add(response);
                }
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
            return result;
        }
    }

    private class LotsOfGreetings implements HelloRunnable {
        @Override
        public List<HelloResponse> run(
                HelloServiceGrpc.HelloServiceBlockingStub blockingStub,
                HelloServiceGrpc.HelloServiceStub asyncStub) {
            final List<HelloResponse> result = new ArrayList<>();
            final CountDownLatch finishLatch = new CountDownLatch(1);
            StreamObserver<HelloResponse> responseObserver = new StreamObserver<HelloResponse>() {
                @Override
                public void onNext(HelloResponse response) {
                    result.add(response);
                }

                @Override
                public void onError(Throwable t) {
                    Log.d(TAG, t.getMessage());
                    finishLatch.countDown();
                }

                @Override
                public void onCompleted() {
                    Log.d(TAG, "LotsOfGreetings onCompleted");
                    finishLatch.countDown();
                }
            };
            StreamObserver<HelloRequest> requestObserver = asyncStub.lotsOfGreetings(responseObserver);
            try {
                HelloRequest request = HelloRequest.newBuilder().setGreeting(GREETING).build();
                for (int i = 0; i < COUNT; i++) {
                    requestObserver.onNext(request);
                }
            } catch (Exception e) {
                requestObserver.onError(e);
            }
            requestObserver.onCompleted();
            try {
                if (!finishLatch.await(1, TimeUnit.MINUTES)) {
                    Log.d(TAG, "Could not finish LotsOfGreetings");
                }
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
            return result;
        }
    }

    private class BidiHello implements HelloRunnable {
        @Override
        public List<HelloResponse> run(
                HelloServiceGrpc.HelloServiceBlockingStub blockingStub,
                HelloServiceGrpc.HelloServiceStub asyncStub) {
            final List<HelloResponse> result = new ArrayList<>();
            final CountDownLatch finishLatch = new CountDownLatch(1);
            StreamObserver<HelloResponse> responseObserver = new StreamObserver<HelloResponse>() {
                @Override
                public void onNext(HelloResponse response) {
                    result.add(response);
                }

                @Override
                public void onError(Throwable t) {
                    Log.d(TAG, t.getMessage());
                    finishLatch.countDown();
                }

                @Override
                public void onCompleted() {
                    Log.d(TAG, "BidiHello onCompleted");
                    finishLatch.countDown();
                }
            };
            StreamObserver<HelloRequest> requestObserver = asyncStub.bidiHello(responseObserver);
            try {
                HelloRequest request = HelloRequest.newBuilder().setGreeting(GREETING).build();
                for (int i = 0; i < COUNT; i++) {
                    requestObserver.onNext(request);
                }
            } catch (Exception e) {
                requestObserver.onError(e);
            }
            requestObserver.onCompleted();
            try {
                if (!finishLatch.await(1, TimeUnit.MINUTES)) {
                    Log.d(TAG, "Could not finish BidiHello");
                }
            } catch (Exception e) {
                Log.d(TAG, e.getMessage());
            }
            return result;
        }
    }

    private class HelloTask extends AsyncTask<Void, Void, List<HelloResponse>> {

        private static final String HOST = "192.168.1.6";
        private static final int PORT = 12345;

        private final HelloRunnable runnable;

        private ManagedChannel mChannel;
        private HelloServiceGrpc.HelloServiceBlockingStub blockingStub;
        private HelloServiceGrpc.HelloServiceStub asyncStub;

        HelloTask(HelloRunnable runnable) {
            this.runnable = runnable;
        }

        @Override
        protected void onPreExecute() {
            try {
                mChannel = ManagedChannelBuilder.forAddress(HOST, PORT).usePlaintext(true).build();
                blockingStub = HelloServiceGrpc.newBlockingStub(mChannel);
                asyncStub = HelloServiceGrpc.newStub(mChannel);
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
        }

        @Override
        protected List<HelloResponse> doInBackground(Void... params) {
            return runnable.run(blockingStub, asyncStub);
        }

        @Override
        protected void onPostExecute(List<HelloResponse> result) {
            for (HelloResponse response : result) {
                Log.d(TAG, response.getReply());
            }
        }
    }
}
