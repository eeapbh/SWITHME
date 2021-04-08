from absl import app, flags, logging
from absl.flags import FLAGS

import tensorflow as tf
import numpy as np
from tensorflow.keras.callbacks import (
    ReduceLROnPlateau,
    EarlyStopping,
    ModelCheckpoint,
    TensorBoard
)
from yolov3.models import (
    YoloV3, YoloLoss,
    yolo_anchors, yolo_anchor_masks,
)
from yolov3.utils import freeze_all
import yolov3.dataset as dataset

flags.DEFINE_string('dataset', './data/raw/wider_face_train.tfrecord', 'path to dataset')
flags.DEFINE_string('val_dataset', './data/raw/wider_face_val.tfrecord', 'path to validation dataset')
flags.DEFINE_string('weights', './checkpoints/origin.tf',
                    'path to weights file')
flags.DEFINE_string('classes', './data/class_name/face.names', 'path to classes file')
flags.DEFINE_enum('mode', 'eager_tf', ['fit', 'eager_fit', 'eager_tf'],
                  'fit: model.fit, '
                  'eager_fit: model.fit(run_eagerly=True), '
                  'eager_tf: custom GradientTape')
flags.DEFINE_enum('transfer', 'darknet',
                  ['none', 'darknet', 'no_output', 'frozen', 'fine_tune'],
                  'none: Training from scratch, '
                  'darknet: Transfer darknet, '
                  'no_output: Transfer all but output, '
                  'frozen: Transfer and freeze all, '
                  'fine_tune: Transfer all and freeze darknet only')
flags.DEFINE_integer('size', 416, 'image size')
flags.DEFINE_integer('epochs', 10, 'number of epochs')
flags.DEFINE_integer('batch_size', 4, 'batch size')
flags.DEFINE_float('learning_rate', 1e-3, 'learning rate')
flags.DEFINE_integer('num_classes', 2, 'number of classes in the model')
flags.DEFINE_integer('weights_num_classes', 80, 'specify num class for `weights` file if different, '
                     'useful in transfer learning with different number of classes')
flags.DEFINE_integer('yolo_max_boxes', 10, 'yolo_max_boxes')


def main(_argv):
    physical_devices = tf.config.experimental.list_physical_devices('GPU')
    tf.config.experimental.set_memory_growth(physical_devices[0], True)

    model = YoloV3(FLAGS.size, training=True, classes=FLAGS.num_classes, yolo_max_boxes=FLAGS.yolo_max_boxes)
    anchors = yolo_anchors
    anchor_masks = yolo_anchor_masks

    # dataset(tf.Tensor(416, 416, 3) - 이미지, tf.Tensor(100, 5) - 앵커 박스 100개)
    train_dataset = dataset.load_tfrecord_dataset(
        FLAGS.dataset, FLAGS.classes, FLAGS.size, FLAGS.yolo_max_boxes)

    # dataset(tf.Tensor(batch_size, 416, 416, 3) - 이미지 묶음, tf.Tensor(batch_size, 100, 5) - 앵커 박스 100개 묶음)
    train_dataset = train_dataset.shuffle(buffer_size=512)
    train_dataset = train_dataset.batch(FLAGS.batch_size)

    # x = transform_images() 반환값으로 변환
    # tf.Tensor(batch_size, 416, 416, 3)
    # y = transform_targets() 반환값으로 변환
    # (tf.Tensor(batch_size, 13, 13, 3, 6), tf.Tensor(batch_size, 26, 26, 3, 6), tf.Tensor(batch_size, 52, 52, 3, 6))
    # transform_images: 이미지 크기 맞추고 정규화
    # transform_targets: (13x13), (26x26), (52x52) 격자로 나누어 3개의 앵커 박스 결과값 도출
    train_dataset = train_dataset.map(lambda x, y: (
        dataset.transform_images(x, FLAGS.size),
        dataset.transform_targets(y, anchors, anchor_masks, FLAGS.size)))

    # 데이터를 나눠서 읽어 현재 데이터를 학습하는 동안 다음 데이터를 준비하여 시간 단축
    train_dataset = train_dataset.prefetch(
        buffer_size=tf.data.experimental.AUTOTUNE)

    # 검증 데이터 불러오기
    val_dataset = dataset.load_tfrecord_dataset(
        FLAGS.val_dataset, FLAGS.classes, FLAGS.size, FLAGS.yolo_max_boxes)

    # 검증 데이터 가공
    val_dataset = val_dataset.batch(FLAGS.batch_size)
    val_dataset = val_dataset.map(lambda x, y: (
        dataset.transform_images(x, FLAGS.size),
        dataset.transform_targets(y, anchors, anchor_masks, FLAGS.size)))

    # 전이 학습 설정
    if FLAGS.transfer == 'none':
        pass  # Nothing to do
    elif FLAGS.transfer in ['darknet', 'no_output']:
        # Darknet transfer is a special case that works
        # with incompatible number of classes

        # reset top layers
        model_pretrained = YoloV3(
            FLAGS.size, training=True, classes=FLAGS.weights_num_classes or FLAGS.num_classes, yolo_max_boxes=FLAGS.yolo_max_boxes)
        model_pretrained.load_weights(FLAGS.weights)

        if FLAGS.transfer == 'darknet':
            model.get_layer('yolo_darknet').set_weights(
                model_pretrained.get_layer('yolo_darknet').get_weights())
            freeze_all(model.get_layer('yolo_darknet'))

        elif FLAGS.transfer == 'no_output':
            for l in model.layers:
                if not l.name.startswith('yolo_output'):
                    l.set_weights(model_pretrained.get_layer(
                        l.name).get_weights())
                    freeze_all(l)

    else:
        # All other transfer require matching classes
        model.load_weights(FLAGS.weights)
        if FLAGS.transfer == 'fine_tune':
            # freeze darknet and fine tune other layers
            darknet = model.get_layer('yolo_darknet')
            freeze_all(darknet)
        elif FLAGS.transfer == 'frozen':
            # freeze everything
            freeze_all(model)

    optimizer = tf.keras.optimizers.Adam(lr=FLAGS.learning_rate)
    loss = [YoloLoss(anchors[mask], classes=FLAGS.num_classes)
            for mask in anchor_masks]

    if FLAGS.mode == 'eager_tf':
        # Eager mode is great for debugging
        # Non eager graph mode is recommended for real training
        avg_loss = tf.keras.metrics.Mean('loss', dtype=tf.float32)
        avg_val_loss = tf.keras.metrics.Mean('val_loss', dtype=tf.float32)

        for epoch in range(1, FLAGS.epochs + 1):
            for batch, (images, labels) in enumerate(train_dataset):
                with tf.GradientTape() as tape:
                    outputs = model(images, training=True)
                    regularization_loss = tf.reduce_sum(model.losses)
                    pred_loss = []
                    for output, label, loss_fn in zip(outputs, labels, loss):
                        pred_loss.append(loss_fn(label, output))
                    total_loss = tf.reduce_sum(pred_loss) + regularization_loss

                grads = tape.gradient(total_loss, model.trainable_variables)
                optimizer.apply_gradients(
                    zip(grads, model.trainable_variables))

                logging.info("{}_train_{}, {}, {}".format(
                    epoch, batch, total_loss.numpy(),
                    list(map(lambda x: np.sum(x.numpy()), pred_loss))))
                avg_loss.update_state(total_loss)

            for batch, (images, labels) in enumerate(val_dataset):
                outputs = model(images)
                regularization_loss = tf.reduce_sum(model.losses)
                pred_loss = []
                for output, label, loss_fn in zip(outputs, labels, loss):
                    pred_loss.append(loss_fn(label, output))
                total_loss = tf.reduce_sum(pred_loss) + regularization_loss

                logging.info("{}_val_{}, {}, {}".format(
                    epoch, batch, total_loss.numpy(),
                    list(map(lambda x: np.sum(x.numpy()), pred_loss))))
                avg_val_loss.update_state(total_loss)

            logging.info("{}, train: {}, val: {}".format(
                epoch,
                avg_loss.result().numpy(),
                avg_val_loss.result().numpy()))

            avg_loss.reset_states()
            avg_val_loss.reset_states()
            model.save_weights(
                'checkpoints/yolov3_train_{}.tf'.format(epoch))
    else:
        model.compile(optimizer=optimizer, loss=loss,
                      run_eagerly=(FLAGS.mode == 'eager_fit'))

        callbacks = [
            ReduceLROnPlateau(verbose=1),
            EarlyStopping(patience=3, verbose=1),
            ModelCheckpoint('checkpoints/yolov3_train_{epoch}.tf',
                            verbose=1, save_weights_only=True),
            TensorBoard(log_dir='logs')
        ]

        history = model.fit(train_dataset,
                            epochs=FLAGS.epochs,
                            callbacks=callbacks,
                            validation_data=val_dataset)


if __name__ == '__main__':
    try:
        app.run(main)
    except SystemExit:
        pass
