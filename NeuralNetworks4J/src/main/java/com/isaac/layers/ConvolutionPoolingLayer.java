package com.isaac.layers;


import com.isaac.initialization.Activation;
import com.isaac.utils.RandomGenerator;

import java.util.Random;
import java.util.function.DoubleFunction;

@SuppressWarnings("unused")
public class ConvolutionPoolingLayer {
	private int[] imageSize;
    private int channel;
    private int nKernel;
    private int[] kernelSize;
    private int[] poolSize;
    private int[] convolvedSize;
    private int[] pooledSize;
    private double[][][][] W;
    private double[] b;
    private Random rng;
    private DoubleFunction<Double> activation;
    private DoubleFunction<Double> dactivation;

    public ConvolutionPoolingLayer(int[] imageSize, int channel, int nKernel, int[] kernelSize, int[] poolSize,
                                   int[] convolvedSize, int[] pooledSize, Random rng, Activation activationMethod) {
        if (rng == null) 
        	rng = new Random(1234);
        if (W == null) {
            W = new double[nKernel][channel][kernelSize[0]][kernelSize[1]];
            double in_ = channel * kernelSize[0] * kernelSize[1];
            double out_ = nKernel * kernelSize[0] * kernelSize[1] / (poolSize[0] * poolSize[1]);
            double w_ = Math.sqrt(6. / (in_ + out_));
            for (int k = 0; k < nKernel; k++) {
                for (int c = 0; c < channel; c++) {
                    for (int s = 0; s < kernelSize[0]; s++) {
                        for (int t = 0; t < kernelSize[1]; t++) {
                            W[k][c][s][t] = RandomGenerator.uniform(-w_, w_, rng);
                        }
                    }
                }
            }
        }
        if (b == null) b = new double[nKernel];
        this.imageSize = imageSize;
        this.channel = channel;
        this.nKernel = nKernel;
        this.kernelSize = kernelSize;
        this.poolSize = poolSize;
        this.convolvedSize = convolvedSize;
        this.pooledSize = pooledSize;
        this.rng = rng;
        activationMethod = activationMethod == null ? Activation.ReLU : activationMethod;
        this.activation = Activation.active(activationMethod);
        this.dactivation = Activation.dactive(activationMethod);
    }


    public double[][][] forward(double[][][] x, double[][][] preActivated_X, double[][][] activated_X) {
        double[][][] z = this.convolve(x, preActivated_X, activated_X);
        return  this.downsample(z);
    }


    public double[][][][] backward(double[][][][] X, double[][][][] preActivated_X, double[][][][] activated_X, 
    		double[][][][] downsampled_X, double[][][][] dY, int minibatchSize, double learningRate) {
        double[][][][] dZ = this.upsample(activated_X, downsampled_X, dY, minibatchSize);
        return this.deconvolve(X, preActivated_X, dZ, minibatchSize, learningRate);
    }



    private double[][][] convolve(double[][][] x, double[][][] preActivated_X, double[][][] activated_X) {
        double[][][] y = new double[nKernel][convolvedSize[0]][convolvedSize[1]];
        for (int k = 0; k < nKernel; k++) {
            for (int i = 0; i < convolvedSize[0]; i++) {
                for(int j = 0; j < convolvedSize[1]; j++) {
                    double convolved_ = 0.;
                    for (int c = 0; c < channel; c++) {
                        for (int s = 0; s < kernelSize[0]; s++) {
                            for (int t = 0; t < kernelSize[1]; t++) {
                                convolved_ += W[k][c][s][t] * x[c][i+s][j+t];
                            }
                        }
                    }
                    // cache pre-activated inputs
                    preActivated_X[k][i][j] = convolved_ + b[k];
                    activated_X[k][i][j] = this.activation.apply(preActivated_X[k][i][j]);
                    y[k][i][j] = activated_X[k][i][j];
                }
            }
        }
        return y;
    }

    private double[][][][] deconvolve(double[][][][] X, double[][][][] Y, double[][][][] dY, int minibatchSize,
                                      double learningRate) {
        double[][][][] grad_W = new double[nKernel][channel][kernelSize[0]][kernelSize[1]];
        double[] grad_b = new double[nKernel];
        double[][][][] dX = new double[minibatchSize][channel][imageSize[0]][imageSize[1]];
        // calc gradients of W, b
        for (int n = 0; n < minibatchSize; n++) {
            for (int k = 0; k < nKernel; k++) {
                for (int i = 0; i < convolvedSize[0]; i++) {
                    for (int j = 0; j < convolvedSize[1]; j++) {
                        double d_ = dY[n][k][i][j] * this.dactivation.apply(Y[n][k][i][j]);
                        grad_b[k] += d_;
                        for (int c = 0; c < channel; c++) {
                            for (int s = 0; s < kernelSize[0]; s++) {
                                for (int t = 0; t < kernelSize[1]; t++) {
                                    grad_W[k][c][s][t] += d_ * X[n][c][i+s][j+t];
                                }
                            }
                        }
                    }
                }
            }
        }
        // update gradients
        for (int k = 0; k < nKernel; k++) {
            b[k] -= learningRate * grad_b[k] / minibatchSize;
            for (int c = 0; c < channel; c++) {
                for (int s = 0; s < kernelSize[0]; s++) {
                    for(int t = 0; t < kernelSize[1]; t++) {
                        W[k][c][s][t] -= learningRate * grad_W[k][c][s][t] / minibatchSize;
                    }
                }
            }
        }
        // calc delta
        for (int n = 0; n < minibatchSize; n++) {
            for (int c = 0; c < channel; c++) {
                for (int i = 0; i < imageSize[0]; i++) {
                    for (int j = 0; j < imageSize[1]; j++) {
                        for (int k = 0; k < nKernel; k++) {
                            for (int s = 0; s < kernelSize[0]; s++) {
                                for (int t = 0; t < kernelSize[1]; t++) {
                                    double d_ = 0.;
                                    if (i - (kernelSize[0] - 1) - s >= 0 && j - (kernelSize[1] - 1) - t >= 0) {
                                        d_ = dY[n][k][i-(kernelSize[0]-1)-s][j-(kernelSize[1]-1)-t] * 
                                        		this.dactivation
                                        		.apply(Y[n][k][i-(kernelSize[0]-1)-s][j-(kernelSize[1]-1)-t]) 
                                        		* W[k][c][s][t];
                                    }
                                    dX[n][c][i][j] += d_;
                                }
                            }
                        }
                    }
                }
            }
        }
        return dX;
    }

    private double[][][] downsample(double[][][] x) {
        double[][][] y = new double[nKernel][pooledSize[0]][pooledSize[1]];
        for (int k = 0; k < nKernel; k++) {
            for (int i = 0; i < pooledSize[0]; i++) {
                for (int j = 0; j < pooledSize[1]; j++) {
                    double max_ = 0.;
                    for (int s = 0; s < poolSize[0]; s++) {
                        for (int t = 0; t < poolSize[1]; t++) {
                            if (s == 0 && t == 0) {
                                max_ = x[k][poolSize[0]*i][poolSize[1]*j];
                                continue;
                            }
                            if (max_ < x[k][poolSize[0]*i+s][poolSize[1]*j+t]) {
                                max_ = x[k][poolSize[0]*i+s][poolSize[1]*j+t];
                            }
                        }
                    }
                    y[k][i][j] = max_;
                }
            }
        }
        return y;
    }

    private double[][][][] upsample(double[][][][] X, double[][][][] Y, double[][][][] dY, int minibatchSize) {
        double[][][][] dX = new double[minibatchSize][nKernel][convolvedSize[0]][convolvedSize[1]];
        for (int n = 0; n < minibatchSize; n++) {
            for (int k = 0; k < nKernel; k++) {
                for (int i = 0; i < pooledSize[0]; i++) {
                    for (int j = 0; j < pooledSize[1]; j++) {
                        for (int s = 0; s < poolSize[0]; s++) {
                            for (int t = 0; t < poolSize[1]; t++) {
                                double d_ = 0.;
                                if (Y[n][k][i][j] == X[n][k][poolSize[0]*i+s][poolSize[1]*j+t]) {
                                    d_ = dY[n][k][i][j];
                                }
                                dX[n][k][poolSize[0]*i+s][poolSize[1]*j+t] = d_;
                            }
                        }
                    }
                }
            }
        }
        return dX;
    }

    /** Getters and Setters */
    public int[] getImageSize() { return imageSize; }
    public void setImageSize(int[] imageSize) { this.imageSize = imageSize; }
    public int getChannel() { return channel; }
    public void setChannel(int channel) { this.channel = channel; }
    public int getnKernel() { return nKernel; }
    public void setnKernel(int nKernel) { this.nKernel = nKernel; }
    public int[] getKernelSize() { return kernelSize; }
    public void setKernelSize(int[] kernelSize) { this.kernelSize = kernelSize; }
    public int[] getPoolSize() { return poolSize; }
    public void setPoolSize(int[] poolSize) { this.poolSize = poolSize; }
    public int[] getConvolvedSize() { return convolvedSize; }
    public void setConvolvedSize(int[] convolvedSize) { this.convolvedSize = convolvedSize; }
    public int[] getPooledSize() { return pooledSize; }
    public void setPooledSize(int[] pooledSize) { this.pooledSize = pooledSize; }
    public double[][][][] getW() { return W; }
    public void setW(double[][][][] w) { W = w; }
    public double[] getB() { return b; }
    public void setB(double[] b) { this.b = b; }
    public Random getRng() { return rng; }
    public void setRng(Random rng) { this.rng = rng; }
}
